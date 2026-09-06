package no.priv.garshol.jslt.playground;

import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class PlaygroundServer {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String INDEX_HTML = "lambda.html";

  public static class JsltHandler extends Handler.Abstract {

    @Override
    public boolean handle(Request request, Response response, Callback callback) {
      String target = request.getHttpURI().getPath();

      if (!"/jslt".equals(target)) {
        return false;
      }

      if ("GET".equalsIgnoreCase(request.getMethod())) {
        try (InputStream stream = Parser.class.getClassLoader().getResourceAsStream(INDEX_HTML)) {
          if (stream == null) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            callback.succeeded();
            return true;
          }

          response.setStatus(HttpStatus.OK_200);
          response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html");

          Content.Source source = Content.Source.from(stream);
          Content.copy(source, response, callback);

        } catch (Exception e) {
          response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
          callback.failed(e);
        }
        return true;
      }

      if ("POST".equalsIgnoreCase(request.getMethod())) {
        try (InputStream requestInput = Request.asInputStream(request);
            OutputStream responseOutput = Content.Sink.asOutputStream(response)) {

          JsonNode body = mapper.readTree(requestInput);
          JsonNode input = mapper.readTree(body.get("json").asText());
          String jslt = body.get("jslt").asText();

          Expression template = Parser.compileString(jslt);
          JsonNode output = template.apply(input);

          // Configure payload response
          response.setStatus(HttpStatus.OK_200);
          response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json");
          mapper.writerWithDefaultPrettyPrinter().writeValue(responseOutput, output);

          callback.succeeded(); // Mark completion successfully

        } catch (Exception e) {
          response.setStatus(HttpStatus.BAD_REQUEST_400);
          response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain");

          try (OutputStream errOutput = Content.Sink.asOutputStream(response);
              PrintStream ps = new PrintStream(errOutput)) {
            e.printStackTrace(ps);
          } catch (Exception ignored) {
          }

          callback.failed(e);
        }
        return true;
      }

      return false;
    }
  }

  public static void main(String[] argv) throws Exception {
    if (argv.length == 0) {
      System.err.println("Usage: java PlaygroundServer <port>");
      System.exit(1);
    }

    Server server = new Server(Integer.parseInt(argv[0]));

    Handler.Sequence handlers = new Handler.Sequence();
    handlers.addHandler(new JsltHandler());
    server.setHandler(handlers);

    server.start();
    server.join();
  }
}
