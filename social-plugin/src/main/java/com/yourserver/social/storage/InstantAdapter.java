package com.yourserver.social.storage;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.time.Instant;

/**
 * Gson TypeAdapter for java.time.Instant.
 * Required because Java 17+ module system prevents reflection access to Instant's private fields.
 */
public class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

    @Override
    public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

    @Override
    public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        return Instant.parse(json.getAsString());
    }
}