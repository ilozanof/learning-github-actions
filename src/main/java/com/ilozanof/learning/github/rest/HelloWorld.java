package com.ilozanof.learning.github.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * @author i.fernandez@nchain.com
 * Copyright (c) 2018-2023 nChain Ltd
 */
@Path("")
public class HelloWorld {

    @GET
    @Path("/helloWorld")
    @Produces(MediaType.APPLICATION_JSON)
    public String helloWorld() {
        return "Hello World!";
    }
}
