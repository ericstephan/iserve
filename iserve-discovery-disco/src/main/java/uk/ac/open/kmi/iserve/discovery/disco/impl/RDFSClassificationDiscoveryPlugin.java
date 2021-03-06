///*
// * Copyright (c) 2013. Knowledge Media Institute - The Open University
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.ac.open.kmi.iserve.discovery.disco.impl;
//
//import com.hp.hpl.jena.query.*;
//import com.hp.hpl.jena.rdf.model.Literal;
//import com.hp.hpl.jena.rdf.model.Resource;
//import com.hp.hpl.jena.vocabulary.RDFS;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import uk.ac.open.kmi.iserve.commons.vocabulary.MSM;
//import uk.ac.open.kmi.iserve.commons.vocabulary.SAWSDL;
//import uk.ac.open.kmi.iserve.commons.vocabulary.WSMO_LITE;
//import uk.ac.open.kmi.iserve.discovery.api.MatchResult;
//import uk.ac.open.kmi.iserve.discovery.disco.DiscoMatchType;
//import uk.ac.open.kmi.iserve.sal.manager.impl.ManagerSingleton;
//
//import javax.ws.rs.core.MultivaluedMap;
//import java.net.MalformedURLException;
//import java.net.URI;
//import java.net.URL;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * Plugin for discovering both services and operations given Functional Classifications
// * using RDFS reasoning. This plugin requires that the underlying Service Manager
// * is based on an RDF Store.
// *
// * @author Jacek Kopecky (Knowledge Media Institute - The Open University)
// * @author Carlos Pedrinaci (Knowledge Media Institute - The Open University)
// */
//public class RDFSClassificationDiscoveryPlugin {
//
//
//    private static final String PLUGIN_NAME = "func-rdfs";
//
//    private static final String PLUGIN_DESCRIPTION =
//            "iServe RDFS functional discovery plugin. Discovers services and " +
//                    "operations based on their functional classification using " +
//                    "subsumption reasoning as supported by the underlying RDF Store.";
//
//    private static final String PLUGIN_VERSION = "v0.3";
//
//    // Discovery Pluging Parameters
//
//    private static final String CLASS_PARAMETER = "class";
//    private static final String CLASS_PARAM_DESCRIPTION = "This parameter should" +
//            "contain a list of 1 or more URLs of concepts identifying the " +
//            "Functional Classifications we wish to use for discovery.";
//
//    private static final Map<String, String> parameterDetails;
//
//    static {
//        parameterDetails = new HashMap<String, String>();
//        parameterDetails.put(CLASS_PARAMETER, CLASS_PARAM_DESCRIPTION);
//    }
//
//    private static final Logger log = LoggerFactory.getLogger(RDFSClassificationDiscoveryPlugin.class);
//
//    public static String NL = System.getProperty("line.separator");
//
//    HashMap<String, Integer> matchTypesValuesMap;
//
//    private int count;
//
//    private String feedSuffix;
//
//    private URI sparqlEndpoint;
//
//    // TODO: Make this a configurable parameter;
//    //	private MatchScorer scorer = new BasicScorer();
//
//    public RDFSClassificationDiscoveryPlugin() {
//        sparqlEndpoint = ManagerSingleton.getInstance().getConfiguration().getDataSparqlUri();
//        if (sparqlEndpoint == null) {
//            log.error("The '" + this.getName() + "' " + this.getVersion() +
//                    " services discovery plugin currently requires a SPARQL endpoint.");
//            // TODO: Disable plugin
//        }
//    }
//
//    /* (non-Javadoc)
//     * @see uk.ac.open.kmi.iserve.discovery.api.DiscoveryPlugin#getName()
//     */
//    public String getName() {
//        return PLUGIN_NAME;
//    }
//
//    /* (non-Javadoc)
//     * @see uk.ac.open.kmi.iserve.discovery.api.DiscoveryPlugin#getDescription()
//     */
//    public String getDescription() {
//        return PLUGIN_DESCRIPTION;
//    }
//
//    /* (non-Javadoc)
//     * @see uk.ac.open.kmi.iserve.discovery.api.IServiceDiscoveryPlugin#getVersion()
//     */
//    public String getVersion() {
//        return PLUGIN_VERSION;
//    }
//
//    /* (non-Javadoc)
//     * @see uk.ac.open.kmi.iserve.discovery.api.DiscoveryPlugin#getFeedTitle()
//     */
//    public String getFeedTitle() {
//        String feedTitle = "RDFS Functional Classification discovery results: " +
//                count + " service(s) or operation(s) for " + feedSuffix;
//        return feedTitle;
//    }
//
//
//    /* (non-Javadoc)
//     * @see uk.ac.open.kmi.iserve.discovery.api.ServiceDiscoveryPlugin#discoverServices(javax.ws.rs.core.MultivaluedMap)
//     */
//    public Map<URL, MatchResult> discoverServices(MultivaluedMap<String, String> parameters) {
//        return discover(false, parameters);
//    }
//
//    /* (non-Javadoc)
//     * @see uk.ac.open.kmi.iserve.discovery.api.OperationDiscoveryPlugin#discoverOperations(javax.ws.rs.core.MultivaluedMap)
//     */
//    public Map<URL, MatchResult> discoverOperations(MultivaluedMap<String, String> parameters) {
//        return discover(true, parameters);
//    }
//
//    /* (non-Javadoc)
//     * @see uk.ac.open.kmi.iserve.discovery.api.DiscoveryPlugin#getParametersDetails()
//     */
//    public Map<String, String> getParametersDetails() {
//        return parameterDetails;
//    }
//
//    /**
//     * @param operationDiscovery
//     * @param parameters
//     * @return
//     */
//    public Map<URL, MatchResult> discover(boolean operationDiscovery, MultivaluedMap<String, String> parameters) {
//
//        // If there is no SPARQL endpoint raise an error
//        if (sparqlEndpoint == null) {
//            log.error("Unable to perform discovery, no SPARQL endpoint available.");
//            throw new RuntimeException("403 Unable to perform discovery, no SPARQL endpoint available.");
//        }
//
//        List<String> classes = parameters.get(CLASS_PARAMETER);
//        if (classes == null || classes.size() == 0) {
//            throw new RuntimeException("403 Functional discovery without parameters is not supported - add parameter 'class=uri'");
//        }
//        for (int i = 0; i < classes.size(); i++) {
//            if (classes.get(i) == null) {
//                throw new RuntimeException("403 Empty class URI not allowed");
//            }
//        }
//
//        Map<URL, MatchResult> results = funcClassificationDisco(operationDiscovery, classes);
//
//        return results;
//    }
//
//    /**
//     * TODO: Fix the scoring to be done properly at the end
//     * TODO: Rearrange to figure out scoring better
//     *
//     * @param operationDiscovery
//     * @param classes
//     * @return
//     */
//    private Map<URL, MatchResult> funcClassificationDisco(boolean operationDiscovery, List<String> classes) {
//
//        Map<URL, MatchResult> results = new HashMap<URL, MatchResult>();
//        // Return immediately if there is nothing to discover
//        if (classes == null || classes.isEmpty()) {
//            return results;
//        }
//
//        String queryStr = generateQuery(operationDiscovery, classes);
//        log.info("Querying for services: \n" + queryStr);
//
//        // Query the engine
//        Query query = QueryFactory.create(queryStr);
//        QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint.toASCIIString(), query);
//
//        URL matchUrl = null;
//        String matchLabel = null;
//        Resource resource;
//        Literal labelResource;
//        String matchBinding;
//        String labelBinding;
//
//        if (operationDiscovery) {
//            matchBinding = "op";
//            labelBinding = "labelOp";
//        } else {
//            matchBinding = "svc";
//            labelBinding = "labelSvc";
//        }
//
//        try {
//            ResultSet qResults = qexec.execSelect();
//            while (qResults.hasNext()) {
//                QuerySolution soln = qResults.nextSolution();
//
//                // Get the match URL
//                resource = soln.getResource(matchBinding);
//                if (resource != null && resource.isURIResource()) {
//                    matchUrl = new URL(resource.getURI());
//                }
//
//                // Get label if it exists
//                labelResource = soln.getLiteral(labelBinding);
//                if (labelResource != null) {
//                    matchLabel = labelResource.toString();
//                }
//
//                if (matchUrl != null) {
//                    // Ensure we got a result before proceeding further
//                    // Create a match result
//                    MatchResultImpl match = new MatchResultImpl(matchUrl, matchLabel);
//
//                    if (soln.contains("sssog0")) {
//                        // Add the result as it is: SSSOG = PLUGIN
//                        match.setMatchType(DiscoMatchType.Plugin);
//                        results.put(match.getMatchedResource().toURL(), match);
//
//                    }
//
//                    // Only add to GSSOS if no gX is missing
//                    boolean lacksGx = false;
//                    boolean isGxSet;
//                    for (int i = 0; i < classes.size(); i++) {
//                        isGxSet = soln.contains("g" + i);
//                        if (!isGxSet) {
//                            lacksGx = true;
//                            break;
//                        }
//                    }
//
//                    if (!lacksGx) {
//                        // The service is either GSSOS (Subsume), or Exact if it is SSOG too
//                        if (results.containsKey(match.getMatchedResource())) {
//                            // If it is there, it's a SSOG too -> Change to Exact
//                            // TODO: Dirty fix, cast to matchResultImpl, setMatchType is not a method of the interface (and should not be)
//                            ((MatchResultImpl) results.get(match.getMatchedResource())).setMatchType(DiscoMatchType.Exact);
//                        } else {
//                            // Change to GSSOS and add to results
//                            match.setMatchType(DiscoMatchType.Subsume);
//                            results.put(match.getMatchedResource().toURL(), match);
//                        }
//                    }
//
//                    //				// By now the match type is already known -> compute score
//                    //				match.setScore(scorer.computeScore(match));
//
//                    // TODO: Add these
//                    // match.setEngineUrl(engineUrl);
//                    // match.setRequest(request);
//                    // Add the result
////					results.put(match.getMatchedResource(), match);
//                }
//            }
//        } catch (MalformedURLException e) {
//            log.error("Error obtaining match result. Expected a correct URL", e);
//        } finally {
//            qexec.close();
//        }
//
//        return results;
//
//    }
//
//    /**
//     * Discovery query for functional classification of items
//     * <p/>
//     * TODO extension: don't care about WSMO-Lite wl:FCR
//     * TODO: what about kinda-gssos where all goal classes are subclasses of
//     * service classes? it's stronger gssos if also all service classes have
//     * goal subclasses.
//     * TODO: Intersection match
//     * - find services for which there exists a category that is a subcategory
//     * of all the categories of both the goal and the svc; but remove any from
//     * above
//     * - also find potential intersections where some service and goal classes
//     * are related through subclass
//     * <p/>
//     * 1 exact match, 2 service subset of goal, 3 goal subset of service:
//     * 1,2 find services that have a subcategory of each of the goal categories
//     * 1,3 find services for which the goal has a subcategory of every service category
//     * <p/>
//     * In the query, the presence of sssog0 means that the service contains
//     * subcategories of all goal categories (service is a subset of goal)
//     * <p/>
//     * The presence of gX means the goal contains a subcategory of a class
//     * category; if every row for a service contains at least one gX then the
//     * goal is a subset of service
//     * <p/>
//     * For operations discovery, operations "inherit" all the categorisations of
//     * the services they belong to, and they can have their own ones.
//     * <p/>
//     * <p/>
//     * Evaluating this query should lead to the following variables:
//     * ?svc - indicates the service
//     * ?labelSvc - contains the label of the service
//     * ?catSvc - contains the categories of the service
//     * ?sssog0 - contains all the sssog
//     * ?op - indicates the op (if necessary)
//     * ?labelOp - contains the label of the operation (if necessary)
//     * ?catOp - contains the categories of the operation (if necessary)
//     * ?gX as in ?g0, ?g1 - contain the goal classes
//     *
//     * @param operationDiscovery
//     * @param classes
//     * @return
//     */
//    private String generateQuery(boolean operationDiscovery,
//                                 List<String> classes) {
//
//        log.debug("Generating query for classes: " + classes.toString());
//
//        StringBuilder query = new StringBuilder().
//                append("select ?svc ?labelSvc ?catSvc ?sssog0");
//        if (operationDiscovery) {
//            query.append(" ?op  ?labelOp  ?catOp ");
//        }
//
//        for (int i = 0; i < classes.size(); i++) {
//            query.append(" ?g" + i + " ");
//        }
//
//        query.append(NL + "where {" + NL);
//        query.append("?svc a <" + MSM.Service.getURI() + "> . " + NL);
//        query.append("optional { ?svc <" + RDFS.label.getURI() + "> ?labelSvc } " + NL);
//
//        // Generate the optional queries for SVC and subclasses of the FC
//        query.append("optional {");
//        query.append("?svc <" + SAWSDL.modelReference.getURI() + "> ?catSvc ." + NL);
//        query.append("?catSvc <" + RDFS.subClassOf.getURI() + "> [ a <" + WSMO_LITE.FunctionalClassificationRoot.getURI() + "> ] ." + NL);
//        for (int i = 0; i < classes.size(); i++) {
//            query.append(" <" + classes.get(i).replace(">", "%3e") + "> <" + RDFS.subClassOf.getURI() + "> ?catSvc ; ?g" + i + " ?catSvc ." + NL);
//        }
//        query.append("  }" + NL);
//        // End
//
//        query.append("  optional {" + NL);
//        for (int i = 0; i < classes.size(); i++) {
//            query.append("    ?svc <" + SAWSDL.modelReference.getURI() + "> ?sssog" + i + " . " + NL);
//            query.append("    ?sssog" + i + " <" + RDFS.subClassOf.getURI() + "> <" + classes.get(i).replace(">", "%3e") + "> ." + NL);
//        }
//        query.append("  }" + NL);
//
//        if (operationDiscovery) {
//            // Obtain operation references
//            query.append("?svc <" + MSM.hasOperation.getURI() + "> ?op . " + NL);
//            query.append("?op a <" + MSM.Operation.getURI() + "> . " + NL);
//            query.append("optional { ?op <" + RDFS.label.getURI() + "> ?labelOp } " + NL);
//
//            // Generate the optional queries for OP and subclasses of the FC
//            query.append("optional {");
//            query.append("?op <" + SAWSDL.modelReference.getURI() + "> ?catOp . " + NL);
//            query.append("?catOp <" + RDFS.subClassOf.getURI() + "> [ a <" + WSMO_LITE.FunctionalClassificationRoot.getURI() + "> ] . " + NL);
//            for (int i = 0; i < classes.size(); i++) {
//                query.append("    <" + classes.get(i).replace(">", "%3e") + "> <" + RDFS.subClassOf.getURI() + "> ?catOp ; ?g" + i + " ?catOp ." + NL);
//            }
//            query.append("  }" + NL);
//
//            query.append("  optional {" + NL);
//            for (int i = 0; i < classes.size(); i++) {
//                query.append("    ?op <" + SAWSDL.modelReference.getURI() + "> ?sssog" + i + " . " + NL);
//                query.append("    ?sssog" + i + " <" + RDFS.subClassOf.getURI() + "> <" + classes.get(i).replace(">", "%3e") + "> ." + NL);
//            }
//            query.append("  }" + NL);
//
//        }
//        query.append("}");
//
//        return query.toString();
//    }
//
//}
