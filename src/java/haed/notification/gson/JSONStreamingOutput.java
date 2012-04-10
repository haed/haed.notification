package haed.notification.gson;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

public class JSONStreamingOutput implements StreamingOutput {
	
	private static GsonBuilder gsonBuilder = new GsonBuilder()
		.registerTypeAdapter(JSONArray.class, new JSONArrayTypeAdapter())
		.registerTypeAdapter(JSONObject.class, new JSONObjectTypeAdapter());
		
  public static void registerTypeAdapter(final Type type, final Object typeAdapter) {
  	gsonBuilder.registerTypeAdapter(type, typeAdapter);
  }
	
	public static Gson createGson() {
		return gsonBuilder.create();
	}
	
	
	private final Object object;
	private final Type type;
	
	private final Gson gson = createGson();
	
	public JSONStreamingOutput(Object object, Type type) {
		this.object = object;
		this.type = type;
	}
	
	@Override
  public void write(final OutputStream outputStream)
  		throws IOException, WebApplicationException {
  	
  	if (object != null) {
	  	final JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(outputStream, "UTF-8"));
	  	try {
	  		gson.toJson(object, type, jsonWriter);
	  	} finally {
	  		jsonWriter.close();
	  	}
  	}
  }
}