/**
 * 
 */
package com.github.phantomthief.model.builder.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Throwables;

/**
 * @author w.vela
 */
public class ToStringUtils {

    private static ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static final String toString(Object obj) {
        try {
            return obj.getClass().getSimpleName() + "=>" + objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }
}
