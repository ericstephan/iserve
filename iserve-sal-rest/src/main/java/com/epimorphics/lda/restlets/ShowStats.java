/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/
package com.epimorphics.lda.restlets;

import com.epimorphics.lda.support.pageComposition.ComposeStatsDisplay;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * The ShowStats restlet provides access to an HTML rendering of
 * Elda's StatsValues.
 */
@Path("/control/show-stats")
public class ShowStats {

    /**
     * Render the statistics into HTML and respond with it.
     */
    @GET
    @Produces("text/html")
    public synchronized Response showStats() {
        return RouterRestlet.returnAs(new ComposeStatsDisplay().renderStatsPage(), "text/html");
    }
}
