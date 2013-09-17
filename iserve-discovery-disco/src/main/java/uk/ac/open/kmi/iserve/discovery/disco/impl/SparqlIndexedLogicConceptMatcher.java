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

package uk.ac.open.kmi.iserve.discovery.disco.impl;


import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.kmi.iserve.discovery.api.MatchResult;
import uk.ac.open.kmi.iserve.discovery.api.MatchType;
import uk.ac.open.kmi.iserve.discovery.api.MatchTypes;
import uk.ac.open.kmi.iserve.discovery.api.MultiMatcher;
import uk.ac.open.kmi.iserve.discovery.api.impl.AtomicMatchResult;
import uk.ac.open.kmi.iserve.discovery.api.impl.EnumMatchTypes;
import uk.ac.open.kmi.iserve.discovery.disco.LogicConceptMatchType;
import uk.ac.open.kmi.iserve.sal.events.OntologyCreatedEvent;
import uk.ac.open.kmi.iserve.sal.events.OntologyDeletedEvent;
import uk.ac.open.kmi.iserve.sal.exception.SalException;
import uk.ac.open.kmi.iserve.sal.manager.iServeManager;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rudimentary implementation of an in-memory index for logic matching.
 * We are assuming here that the ontologies are in a remote server exposed through a SPARQL endpoint. If we can sit in
 * the same server we should try and use the libraries from the store directly to avoid replicating the index.
 */
public class SparqlIndexedLogicConceptMatcher implements MultiMatcher {

    private static final Logger log = LoggerFactory.getLogger(SparqlIndexedLogicConceptMatcher.class);

    private static final MatchTypes<MatchType> matchTypes = EnumMatchTypes.of(LogicConceptMatchType.class);

    private Table<URI, URI, MatchResult> indexedMatches;
    private iServeManager manager;
    private SparqlLogicConceptMatcher sparqlMatcher;

    @Inject
    public SparqlIndexedLogicConceptMatcher(iServeManager iServeManager,
                                            SparqlLogicConceptMatcher sparqlMatcher) throws SalException {

        this.sparqlMatcher = sparqlMatcher;
        this.manager = iServeManager;
        iServeManager.registerAsObserver(this);
        this.indexedMatches = populate();
    }

    private Table<URI, URI, MatchResult> populate() {
        Set<URI> classes = new HashSet<URI>(this.manager.getKnowledgeBaseManager().listConcepts(null));
        return sparqlMatcher.listMatchesAtLeastOfType(classes, LogicConceptMatchType.Plugin);
    }

    @Override
    public String getMatcherDescription() {
        return "Indexed Logic Concept Matcher";
    }

    @Override
    public String getMatcherVersion() {
        return "1.0";
    }

    /**
     * Obtains the MatchTypes instance that contains the MatchTypes supported as well as their ordering information
     *
     * @return
     */
    @Override
    public MatchTypes<MatchType> getMatchTypesSupported() {
        return this.matchTypes;
    }

    @Override
    public MatchResult match(final URI origin, final URI destination) {
        MatchResult result = this.indexedMatches.get(origin, destination);
        // If there are no entries for origin,dest assume fail.
        if (result == null) {
            result = new AtomicMatchResult(origin, destination, LogicConceptMatchType.Fail, this);
        }
        return result;
    }


    /**
     * Perform a match between two Sets of URIs (from {@code origin} to {@code destination})
     * and returns the result.
     *
     * @param origins      Set of URIs of the elements to match
     * @param destinations Set of URIs of the elements to match against
     * @return a {@link com.google.common.collect.Table} with the result of the matching indexed by origin URI and then destination URI.
     */
    @Override
    public Table<URI, URI, MatchResult> match(Set<URI> origins, Set<URI> destinations) {
        ImmutableTable.Builder<URI, URI, MatchResult> builder = ImmutableTable.builder();
        for (URI origin : origins) {
            for (URI destination : destinations) {
                MatchResult mr = this.indexedMatches.get(origin, destination);
                if (mr == null) {
                    mr = new AtomicMatchResult(origin, destination, LogicConceptMatchType.Fail, this);
                }

                builder.put(origin, destination, mr);
            }
        }
        return builder.build();
    }

    // Process events to update the indexes

    /**
     * A new ontology has been uploaded to the server and we need to update the indexes
     *
     * @param event the actual event that was triggered
     */
    @Subscribe
    public void handleOntologyCreated(OntologyCreatedEvent event) {

        log.info("Processing Ontology Created Event - {}", event.getOntologyUri());

        // Obtain the concepts in the ontology uploaded
        List<URI> conceptUris = this.manager.getKnowledgeBaseManager().listConcepts(event.getOntologyUri());
        log.info("Fetching matches for all the {} concepts present in the ontology - {}", conceptUris.size(), event.getOntologyUri());

        // For each of them update their entries in the index (matched concepts will be updated later within the loop)
        Table<URI, URI, MatchResult> matches = this.sparqlMatcher.listMatchesAtLeastOfType(new HashSet<URI>(conceptUris), LogicConceptMatchType.Subsume);
        this.indexedMatches.putAll(matches);
    }

    /**
     * An ontology has been deleted from the KB and we should thus update the indexes. By the time we want to process
     * this event we won't know any longer which concepts need to be removed unless we keep this somewhere.
     *
     * @param event the ontology deletion event
     */
    @Subscribe
    public void handleOntologyDeleted(OntologyDeletedEvent event) {

        log.info("Processing Ontology Deleted Event - {}", event.getOntologyUri());

        // Obtain all concepts in the KB
        List<URI> conceptUris = this.manager.getKnowledgeBaseManager().listConcepts(null);

        // Cross reference them with those for which we have entries. The ones unmatched were defined in the previous
        // ontology (note that tse of namespaces could be abused and therefore may not be a guarantee).
        Set<URI> difference = Sets.difference(new HashSet<URI>(conceptUris), this.indexedMatches.rowKeySet());

        // Eventually remove these rows and any value having that as a column.
        for (URI conceptUri : difference) {
            // remove all rows matched to the concept
            this.indexedMatches.remove(conceptUri, null);
            // remove all columns matched to the concept
            this.indexedMatches.remove(null, conceptUri);
        }

    }

}
