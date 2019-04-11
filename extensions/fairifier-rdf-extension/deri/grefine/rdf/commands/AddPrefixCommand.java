package org.deri.grefine.rdf.commands;

import java.io.IOException;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.deri.grefine.rdf.app.ApplicationContext;
import org.deri.grefine.rdf.vocab.PrefixExistException;
import org.deri.grefine.rdf.vocab.VocabularyImporter;
import org.json.JSONException;
import org.json.JSONWriter;
import com.google.refine.Jsonizable;

public class AddPrefixCommand extends RdfCommand {

    public AddPrefixCommand(ApplicationContext ctxt) {
        super(ctxt);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String name = request.getParameter("name").trim();
        String uri = request.getParameter("uri").trim();
        String projectId = request.getParameter("project");
        try {
            getRdfSchema(request).addPrefix(name, uri);
            String fetchUrl = request.getParameter("fetch-url");
            if (fetchUrl == null) {
                fetchUrl = uri;
            }
            getRdfContext().getVocabularySearcher().importAndIndexVocabulary(name, uri, fetchUrl,
                    projectId, new VocabularyImporter());
            respondJSON(response, new Jsonizable() {

                @Override
                public void write(JSONWriter writer, Properties options) throws JSONException {
                    writer.object();
                    writer.key("code");
                    writer.value("ok");
                    writer.endObject();
                }
            });
        } catch (JSONException e) {
            respondException(response, e);
        } catch (PrefixExistException e) {
            respondException(response, e);
        } catch (Exception e) {
            respondException(response, e);
        }
    }
}
