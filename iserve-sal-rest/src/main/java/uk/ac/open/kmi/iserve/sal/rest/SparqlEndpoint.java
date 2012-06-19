/*
   Copyright ${year}  Knowledge Media Institute - The Open University

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package uk.ac.open.kmi.iserve.sal.rest;

import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.rdf2go.RepositoryModel;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;

import uk.ac.open.kmi.iserve.commons.io.RDFRepositoryConnector;
import uk.ac.open.kmi.iserve.sal.manager.impl.ServiceManagerRdf;
import uk.ac.open.kmi.iserve.sal.manager.impl.ManagerSingleton;

@Path("/")
public class SparqlEndpoint {

	private ServiceManagerRdf serviceManager;

	@Context
	UriInfo uriInfo;

	public SparqlEndpoint() { }

	/**
	 * Provides querying access support to the underlying services registry
	 * Access to log information or users information is not provided
	 * 
	 * @param queryString the SPARQL query string
	 * @return the response
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws QueryEvaluationException
	 * @throws TupleQueryResultHandlerException
	 * @throws RDFHandlerException
	 * @throws IOException
	 */
	@GET
	@Produces({"application/sparql-results+xml", "application/rdf+xml", MediaType.WILDCARD})
	public Response query(@QueryParam("query") String queryString) throws RepositoryException, MalformedQueryException, QueryEvaluationException, TupleQueryResultHandlerException, RDFHandlerException, IOException {
		String absolutePath = uriInfo.getAbsolutePath().toString();
		if ( absolutePath.endsWith("sparql/") || absolutePath.endsWith("execute-query/") ||
				absolutePath.endsWith("sparql") || absolutePath.endsWith("execute-query")) {
			
			RDFRepositoryConnector connector = ManagerSingleton.getInstance().getServicesRepositoryConnector();
			String result = connector.query(queryString);

			if ( result.contains("http://www.w3.org/2005/sparql-results") ) {
				return Response.ok(result, "application/sparql-results+xml").build();
			} else {
				return Response.ok(result, "application/rdf+xml").build();
			}
		}
		return Response.status(Status.BAD_REQUEST).build();
	}

}
