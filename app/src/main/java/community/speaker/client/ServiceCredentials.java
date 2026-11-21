package community.speaker.client;

import org.json.JSONException;
import org.json.JSONObject;

final class ServiceCredentials {
    private final JSONObject values;

    private ServiceCredentials(JSONObject values) {
        this.values = values;
    }

    static ServiceCredentials empty() {
        return new ServiceCredentials(new JSONObject());
    }

    static ServiceCredentials parse(String json) throws JSONException {
        return new ServiceCredentials(new JSONObject(json));
    }

    String get(String name) {
        return values.optString(name, "");
    }

    boolean contains(String name) {
        return values.has(name) && !values.isNull(name);
    }

    ServiceCredentials with(String name, String value) throws JSONException {
        JSONObject copy = new JSONObject(values.toString());
        if (value == null || value.trim().isEmpty()) copy.remove(name);
        else copy.put(name, value.trim());
        return new ServiceCredentials(copy);
    }

    boolean isEmpty() {
        return values.length() == 0;
    }

    String toJson() {
        return values.toString();
    }
}
