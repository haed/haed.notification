package haed.notification.gson;

import java.lang.reflect.Type;
import java.util.Iterator;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class JSONObjectTypeAdapter implements JsonSerializer<JSONObject> {
	
	public JsonElement serialize(final JSONObject src, final Type typeOfSrc, final JsonSerializationContext context) {
		
		try {
			
			final JsonObject jsonObject = new JsonObject();
			for (@SuppressWarnings("unchecked") Iterator<String> keyIter = src.keys(); keyIter.hasNext(); ) {
				final String key = keyIter.next();
				jsonObject.add(key, context.serialize(src.get(key)));
			}
			
			return jsonObject;
			
		} catch (final JSONException e) {
			// TODO [haed]: exception handling?
			throw new RuntimeException(e);
		}
  }
}