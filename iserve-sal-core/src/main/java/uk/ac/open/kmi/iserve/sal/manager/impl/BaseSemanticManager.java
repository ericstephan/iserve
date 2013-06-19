/*
 * Copyright (c) 2013. Knowledge Media Institute - The Open University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.open.kmi.iserve.sal.manager.impl;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.shared.PrefixMapping;
import com.hp.hpl.jena.sparql.modify.request.Target;
import com.hp.hpl.jena.sparql.modify.request.UpdateCreate;
import com.hp.hpl.jena.sparql.modify.request.UpdateDrop;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.kmi.iserve.commons.model.util.Vocabularies;
import uk.ac.open.kmi.iserve.sal.SystemConfiguration;
import uk.ac.open.kmi.iserve.sal.exception.SalException;

import java.io.StringWriter;
import java.net.URI;

public class BaseSemanticManager {

    private static final Logger log = LoggerFactory.getLogger(BaseSemanticManager.class);

    private SystemConfiguration configuration;
    private URI sparqlQueryEndpoint;
    // To use SPARQL Update for modification
    private URI sparqlUpdateEndpoint;
    // To use SPARQL HTTP protocol for graph modification
    private URI sparqlServiceEndpoint;

    private DatasetAccessor datasetAccessor;

    protected BaseSemanticManager(SystemConfiguration configuration) throws SalException {
        this.configuration = configuration;
        this.sparqlQueryEndpoint = configuration.getDataSparqlUri();
        this.sparqlUpdateEndpoint = configuration.getDataSparqlUpdateUri();
        this.sparqlServiceEndpoint = configuration.getDataSparqlServiceUri();
        if (this.sparqlQueryEndpoint == null) {
            log.error(BaseSemanticManager.class.getSimpleName() + " requires a SPARQL Query endpoint.");
            throw new SalException(BaseSemanticManager.class.getSimpleName() + " requires a SPARQL Query endpoint.");
        }

        if (this.sparqlUpdateEndpoint == null && sparqlServiceEndpoint == null) {
            log.warn(BaseSemanticManager.class.getSimpleName() + " requires a SPARQL Update endpoint to modify data.");
        }

        if (this.sparqlServiceEndpoint != null) {
            this.datasetAccessor = DatasetAccessorFactory.createHTTP(this.sparqlServiceEndpoint.toASCIIString());
        }
    }

    /**
     * @return the configuration
     */
    public SystemConfiguration getConfiguration() {
        return this.configuration;
    }

    /**
     * @return the sparqlQueryEndpoint
     */
    public URI getSparqlQueryEndpoint() {
        return this.sparqlQueryEndpoint;
    }

    /**
     * @return the SPARQL update endpoint
     */
    public URI getSparqlUpdateEndpoint() {
        return sparqlUpdateEndpoint;
    }

    /**
     * @return the URI of the SPARQL Service
     */
    public URI getSparqlServiceEndpoint() {
        return sparqlServiceEndpoint;
    }

    public boolean canBeModified() {
        return (sparqlServiceEndpoint != null || sparqlUpdateEndpoint != null);
    }

    /**
     * Add statements to a named model
     *
     * @param graphUri
     * @param data
     */
    protected void addModelToGraph(String graphUri, Model data) {
        if (graphUri == null || data == null)
            return;

        // Use HTTP protocol if possible
        if (this.sparqlServiceEndpoint != null) {
            datasetAccessor.add(graphUri, data);
        } else {
            this.addModelToGraphSparqlQuery(graphUri, data);
        }
    }

    protected void addModelToGraphSparqlQuery(String graphUri, Model data) {
        // TODO: Implement
    }

    /**
     * Does the Dataset contain a named graph?
     *
     * @param graphUri
     * @return
     */
    protected boolean containsGraph(String graphUri) {

        if (graphUri == null)
            return false;

        // Use HTTP protocol if possible
        if (this.sparqlServiceEndpoint != null) {
            return datasetAccessor.containsModel(graphUri);
        } else {
            return this.containsGraphSparqlQuery(graphUri);
        }

    }

    private boolean containsGraphSparqlQuery(String graphUri) {

        if (graphUri == null)
            return true; // Default graph always exists

        StringBuilder queryStr = new StringBuilder("ASK { GRAPH <").append(graphUri).append("> {?s a ?o} } \n");

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.sparqlService(this.getSparqlQueryEndpoint().toASCIIString(), query);
        try {
            return qexec.execAsk();
        } finally {
            qexec.close();
        }
    }

    /**
     * Delete a named model of a Dataset
     *
     * @param graphUri
     */
    protected void deleteGraph(String graphUri) {

        if (graphUri == null || !this.canBeModified())
            return;

        // Use HTTP protocol if possible
        if (this.sparqlServiceEndpoint != null) {
            this.datasetAccessor.deleteModel(graphUri);
        } else {
            deleteGraphSparqlUpdate(graphUri);
        }

        log.debug("Graph deleted: " + graphUri);
    }

    /**
     * Delete Graph using SPARQL Update
     *
     * @param graphUri
     */
    private void deleteGraphSparqlUpdate(String graphUri) {
        UpdateRequest request = UpdateFactory.create();
        request.setPrefixMapping(PrefixMapping.Factory.create().setNsPrefixes(Vocabularies.prefixes));
        request.add(new UpdateDrop(graphUri));

        // Use create form for Sesame-based engines. TODO: Generalise and push to config.
        UpdateProcessor processor = UpdateExecutionFactory.createRemoteForm(request, this.getSparqlUpdateEndpoint().toASCIIString());
        processor.execute(); // TODO: anyway to know if things went ok?
    }

    /**
     * Gets the default model of a Dataset
     *
     * @return
     */
    protected OntModel getGraph() {

        // Use HTTP protocol if possible
        if (this.sparqlServiceEndpoint != null) {
            return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, datasetAccessor.getModel());
        } else {
            return this.getGraphSparqlQuery();
        }
    }

    private OntModel getGraphSparqlQuery() {
        return getGraphSparqlQuery(null);

    }

    /**
     * Get a named model of a Dataset
     *
     * @param graphUri
     * @return
     */
    protected OntModel getGraph(String graphUri) {
        log.debug("Obtaining graph: " + graphUri);

        if (graphUri == null)
            return null;

        // FIXME: For now Jena and Sesame can't talk to each other
        // Jena will ignore a named graph as the output.
        // Use HTTP protocol if possible
//        if (this.sparqlServiceEndpoint != null) {
//            Model model = datasetAccessor.getModel(graphUri);
//            return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
//
//        } else {
        return this.getGraphSparqlQuery(graphUri);
//        }
    }

    /**
     * Obtains the OntModel within a named graph or the default graph if no graphUri is provided
     *
     * @param graphUri
     * @return
     */
    private OntModel getGraphSparqlQuery(String graphUri) {

        StringBuilder queryStr = new StringBuilder("CONSTRUCT { ?s ?p ?o } \n")
                .append("WHERE {\n");

        // Add named graph if necessary
        if (graphUri != null) {
            queryStr.append("GRAPH <").append(graphUri).append("> ");
        }
        queryStr.append("{ ?s ?p ?o } } \n");

        log.debug("Querying graph store:" + queryStr.toString());

        Query query = QueryFactory.create(queryStr.toString());
        QueryExecution qexec = QueryExecutionFactory.sparqlService(this.getSparqlQueryEndpoint().toASCIIString(), query);
        OntModel resultModel = ModelFactory.createOntologyModel();
        try {
            qexec.execConstruct(resultModel);
            return resultModel;
        } finally {
            qexec.close();
        }
    }

    /**
     * Put (create/replace) the default graph of a Dataset
     *
     * @param data
     */
    protected void putGraph(Model data) {

        if (data == null)
            return;

        // Use HTTP protocol if possible
        if (this.sparqlServiceEndpoint != null) {
            datasetAccessor.putModel(data);
        } else {
            this.putGraphSparqlQuery(data);
        }

    }

    private void putGraphSparqlQuery(Model data) {
        this.putGraphSparqlQuery(null, data);
    }

    /**
     * Put (create/replace) a named graph of a Dataset
     *
     * @param graphUri
     * @param data
     */
    protected void putGraph(String graphUri, Model data) {
        if (graphUri == null || data == null)
            return;

        // Use HTTP protocol if possible
        if (this.sparqlServiceEndpoint != null) {
            datasetAccessor.putModel(graphUri, data);
        } else {
            this.putGraphSparqlQuery(graphUri, data);
        }
    }

    private void putGraphSparqlQuery(String graphUri, Model data) {

        UpdateRequest request = UpdateFactory.create();
        request.setPrefixMapping(PrefixMapping.Factory.create().setNsPrefixes(Vocabularies.prefixes));
        request.add(new UpdateCreate(graphUri));
        request.add(generateInsertRequest(graphUri, data));
        log.debug("Sparql Update Query issued: " + request.toString());

        // Use create form for Sesame-based engines. TODO: Generalise and push to config.
        UpdateProcessor processor = UpdateExecutionFactory.createRemoteForm(request, this.getSparqlUpdateEndpoint().toASCIIString());
        processor.execute(); // TODO: anyway to know if things went ok?
    }

    private String generateInsertRequest(String graphUri, Model data) {

        StringWriter out = new StringWriter();
        data.write(out, uk.ac.open.kmi.iserve.commons.io.Syntax.TTL.getName());
        StringBuilder updateSB = new StringBuilder();
        updateSB.append("INSERT DATA { \n");
        // If a graph is given use it, otherwise insert in the default graph.
        if (graphUri != null) {
            updateSB.append("GRAPH <").append(graphUri).append(">");
        }
        updateSB.append(" { \n");
        updateSB.append(out.getBuffer());
        updateSB.append("}\n");
        updateSB.append("}\n");

        String result = updateSB.toString();

        return result;
    }

    /**
     * Empties the entire Dataset
     */
    protected void clearDataset() {

        // Deleting via graph store protocol does not work apparently.
        // Use HTTP protocol if possible
//        if (this.sparqlServiceEndpoint != null) {
//            datasetAccessor.deleteDefault();
//        } else {
        clearDatasetSparqlUpdate();
//        }
    }

    private void clearDatasetSparqlUpdate() {
        UpdateRequest request = UpdateFactory.create();
        request.add(new UpdateDrop(Target.ALL));
        UpdateProcessor processor = UpdateExecutionFactory.createRemoteForm(request,
                this.getSparqlUpdateEndpoint().toASCIIString());
        processor.execute(); // TODO: anyway to know if things went ok?
        log.debug("Dataset cleared.");
    }
}
