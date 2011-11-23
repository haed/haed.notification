package haed.notification.gson;

import java.lang.reflect.Type;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class JSONArrayTypeAdapter implements JsonSerializer<JSONArray> {
	
	public JsonElement serialize(final JSONArray src, final Type typeOfSrc, final JsonSerializationContext context) {
		
		try {
			
			final JsonArray jsonArray = new JsonArray();
			for (int i = 0; i < src.length(); i++)
				jsonArray.add(context.serialize(src.get(i)));
			
			return jsonArray;
			
		} catch (final JSONException e) {
			// TODO [haed]: exception handling?
			throw new RuntimeException(e);
		}
  }
}