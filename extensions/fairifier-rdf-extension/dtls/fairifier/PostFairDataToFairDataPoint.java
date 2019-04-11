
package org.dtls.fairifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.deri.grefine.rdf.utils.HttpUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import com.google.common.net.HttpHeaders;
import com.google.refine.commands.Command;
import nl.dtl.fairmetadata4j.io.MetadataException;
import nl.dtl.fairmetadata4j.model.Agent;
import nl.dtl.fairmetadata4j.model.CatalogMetadata;
import nl.dtl.fairmetadata4j.model.DatasetMetadata;
import nl.dtl.fairmetadata4j.model.DistributionMetadata;
import nl.dtl.fairmetadata4j.model.Metadata;
import nl.dtl.fairmetadata4j.utils.MetadataUtils;

/**
 * 
 * @author Shamanou van Leeuwen
 * @date 7-11-2016
 *
 */

public class PostFairDataToFairDataPoint extends Command {

    private final static SimpleValueFactory FACTORY = SimpleValueFactory.getInstance();
    private final static String TITLEPREDICATE = "http://purl.org/dc/terms/title";
    private final static String DESCRIPTIONPREDICATE = "http://purl.org/dc/terms/description";
    private final static String VERSIONPREDICATE = "http://purl.org/dc/terms/hasVersion";
    private final static String HOMEPAGEPREDICATE = "http://xmlns.com/foaf/0.1/homepage";
    private final static String LANGUAGEPREDICATE = "http://purl.org/dc/terms/language";
    private final static String THEMETAXONOMYPREDICATE = "http://www.w3.org/ns/dcat#themeTaxonomy";
    private final static String LANDINGPAGEPREDICATE = "http://www.w3.org/ns/dcat#landingPage";
    private final static String THEMEPREDICATE = "http://www.w3.org/ns/dcat#theme";
    private final static String KEYWORDPREDICATE = "http://www.w3.org/ns/dcat#keyword";
    private final static String CONTACTPOINTPREDICATE = "http://www.w3.org/ns/dcat#contactPoint";
    private final static String METADATAIDENTIFIERPREDICATE =
            "http://rdf.biosemantics.org/ontologies/fdp-o#metadataIdentifier";
    private final static String LICENSEPREDICATE = "http://purl.org/dc/terms/license";
    private final static String RIGHTSPREDICATE = "http://purl.org/dc/terms/rights";
    private final static String PUBLISHERPREDICATE = "http://purl.org/dc/terms/publisher";

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        final String uuid_catalog = UUID.randomUUID().toString();
        final String uuid_dataset = UUID.randomUUID().toString();
        final String uuid_distribution = UUID.randomUUID().toString();

        try {
            StringBuffer jb = new StringBuffer();
            String line = null;
            BufferedReader reader = req.getReader();
            while ((line = reader.readLine()) != null) {
                jb.append(line);
            }
            final JSONObject fdp = new JSONObject(jb.toString()).getJSONObject("metadata");
            final JSONObject catalog = fdp.getJSONObject("catalog");
            final JSONObject dataset = fdp.getJSONObject("dataset");
            final JSONObject distribution = fdp.getJSONObject("distribution");
            final JSONObject uploadConfiguration = fdp.getJSONObject("uploadConfiguration");

            String catalogPost = null;
            String catalogHeaderLocation = null;
            if (!catalog.getBoolean("_exists")) {
                CatalogMetadata catalogMetadata =
                        addPropertiesToCatalog(catalog, uuid_catalog, fdp);
                HttpResponse response = pushMetadataToFdp(uuid_catalog, catalogMetadata, "catalog",
                        fdp.getString("baseUri"));
                catalogPost = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
                catalogHeaderLocation = response.getFirstHeader(HttpHeaders.LOCATION).getValue();
            } else {
                catalogHeaderLocation = catalog.getString(METADATAIDENTIFIERPREDICATE);
            }

            String datasetPost = null;
            String datasetHeaderLocation = null;
            if (!dataset.getBoolean("_exists")) {
                DatasetMetadata datasetMetadata = addPropertiesToDataset(dataset, uuid_dataset, fdp,
                        catalog, catalogHeaderLocation);
                HttpResponse response = pushMetadataToFdp(uuid_dataset, datasetMetadata, "dataset",
                        fdp.getString("baseUri"));
                datasetPost = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
                datasetHeaderLocation = response.getFirstHeader(HttpHeaders.LOCATION).getValue();
            } else {
                datasetHeaderLocation = dataset.getString(METADATAIDENTIFIERPREDICATE);
            }

            String data = new JSONObject(jb.toString()).getString("data");

            DistributionMetadata distributionMetadata =
                    addPropertiesToDistribution(distribution, uuid_distribution,
                            datasetHeaderLocation, fdp, dataset, uploadConfiguration, data);

            HttpResponse response = pushMetadataToFdp(uuid_distribution, distributionMetadata,
                    "distribution", fdp.getString("baseUri"));
            String distributionPost = IOUtils.toString(response.getEntity().getContent(), "UTF-8");

            PushFairDataToResourceAdapter adapter = new PushFairDataToResourceAdapter();
            Resource r = null;

            String name =
                    "FAIRdistribution_" + distribution.getString(TITLEPREDICATE).replace(" ", "_")
                            + "_" + distribution.getString(VERSIONPREDICATE).replace(" ", "_");

            if (fdp.getString("uploadtype").equals("ftp")) {
                r = new FtpResource(uploadConfiguration.getString("host"),
                        uploadConfiguration.getString("username"),
                        uploadConfiguration.getString("password"),
                        uploadConfiguration.getString("directory"), name + ".ttl");
            } else if (fdp.getString("uploadtype").equals("virtuoso")) {
                r = new VirtuosoResource(uploadConfiguration.getString("host"), name + ".ttl",
                        uploadConfiguration.getString("username"),
                        uploadConfiguration.getString("password"),
                        uploadConfiguration.getString("directory"));
            }
            r.setFairData(data);
            adapter.setResource(r);
            adapter.push();

            res.setCharacterEncoding("UTF-8");
            res.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            JSONWriter writer = new JSONWriter(res.getWriter());
            writer.object();
            writer.key("code");
            writer.value("ok");
            if (!catalog.getBoolean("_exists")) {
                writer.key("catalogPost");
                writer.value(catalogPost);
            }
            if (!dataset.getBoolean("_exists")) {
                writer.key("datasetPost");
                writer.value(datasetPost);
            }
            writer.key("distributionPost");
            writer.value(distributionPost);
            writer.endObject();

        } catch (IOException ex) {
            respondException(res, ex);
        } catch (JSONException ex) {
            respondException(res, ex);
        } catch (InstantiationException ex) {
            respondException(res, ex);
        } catch (MetadataException ex) {
            respondException(res, ex);
        } catch (IllegalAccessException ex) {
            respondException(res, ex);
        } catch (HttpException ex) {
            respondException(res, ex);
        }
    }

    private CatalogMetadata addPropertiesToCatalog(JSONObject catalog, String uuid_catalog,
            JSONObject fdp) throws JSONException, InstantiationException, IllegalAccessException {
        String name = catalog.getString(TITLEPREDICATE).replace(" ", "_") + "_"
                + catalog.getString(VERSIONPREDICATE).replace(" ", "_");

        CatalogMetadata catalogMetadata = getMetadata(CatalogMetadata.class, catalog,
                fdp.getString("baseUri") + "/catalog/" + name + "_" + uuid_catalog);

        if (catalog.has(HOMEPAGEPREDICATE)) {
            catalogMetadata.setHomepage(FACTORY.createIRI(catalog.getString(HOMEPAGEPREDICATE)));
        }
        ArrayList<IRI> catalogThemes = new ArrayList<IRI>();
        catalogThemes.add(FACTORY.createIRI(catalog.getString(THEMETAXONOMYPREDICATE)));
        catalogMetadata.setThemeTaxonomys(catalogThemes);

        if (catalog.has(DESCRIPTIONPREDICATE)) {
            catalogMetadata
                    .setDescription(FACTORY.createLiteral(catalog.getString(DESCRIPTIONPREDICATE)));
        }
        if (catalog.has(LANGUAGEPREDICATE) && (catalog.getJSONArray(LANGUAGEPREDICATE) != null)) {
            for (int i = 0; i < catalog.getJSONArray(LANGUAGEPREDICATE).length(); i++) {
                if (!catalog.getJSONArray(LANGUAGEPREDICATE).getString(i).trim().equals("")) {
                    catalogMetadata.setLanguage(FACTORY
                            .createIRI(catalog.getJSONArray(LANGUAGEPREDICATE).getString(i)));
                }
            }
        }
        if (catalog.has(HOMEPAGEPREDICATE)) {
            catalogMetadata.setHomepage(FACTORY.createIRI(catalog.getString(HOMEPAGEPREDICATE)));
        }
        return catalogMetadata;
    }

    private DatasetMetadata addPropertiesToDataset(JSONObject dataset, String uuid_dataset,
            JSONObject fdp, JSONObject catalog, String catalog_uri)
            throws JSONException, InstantiationException, IllegalAccessException {

        String name = dataset.getString(TITLEPREDICATE).replace(" ", "_") + "_"
                + dataset.getString(VERSIONPREDICATE).replace(" ", "_");

        DatasetMetadata datasetMetadata = getMetadata(DatasetMetadata.class, dataset,
                fdp.getString("baseUri") + "/dataset/" + name + "_" + uuid_dataset);
        if (dataset.has(LANDINGPAGEPREDICATE)) {
            datasetMetadata
                    .setLandingPage(FACTORY.createIRI(dataset.getString(LANDINGPAGEPREDICATE)));
        }
        ArrayList<IRI> datasetThemes = new ArrayList<IRI>();
        for (int i = 0; i < dataset.getJSONArray(THEMEPREDICATE).length(); i++) {
            datasetThemes.add(FACTORY.createIRI(dataset.getJSONArray(THEMEPREDICATE).getString(i)));
        }
        datasetMetadata.setThemes(datasetThemes);

        if (dataset.has(KEYWORDPREDICATE)) {
            ArrayList<Literal> keyWords = new ArrayList<Literal>();
            for (int i = 0; i < dataset.getJSONArray(KEYWORDPREDICATE).length(); i++) {
                keyWords.add(
                        FACTORY.createLiteral(dataset.getJSONArray(KEYWORDPREDICATE).getString(i)));
            }
            datasetMetadata.setKeywords(keyWords);
        }
        if (dataset.has(CONTACTPOINTPREDICATE)) {
            datasetMetadata
                    .setContactPoint(FACTORY.createIRI(dataset.getString(CONTACTPOINTPREDICATE)));
        }
        if (dataset.has(LANGUAGEPREDICATE) && (dataset.getJSONArray(LANGUAGEPREDICATE) != null)) {
            for (int i = 0; i < dataset.getJSONArray(LANGUAGEPREDICATE).length(); i++) {
                if (!dataset.getJSONArray(LANGUAGEPREDICATE).getString(i).trim().equals("")) {
                    datasetMetadata.setLanguage(FACTORY
                            .createIRI(dataset.getJSONArray(LANGUAGEPREDICATE).getString(i)));
                }
            }
        }
        if (dataset.has(DESCRIPTIONPREDICATE)) {
            datasetMetadata
                    .setDescription(FACTORY.createLiteral(dataset.getString(DESCRIPTIONPREDICATE)));
        }

        datasetMetadata.setParentURI(FACTORY.createIRI(catalog_uri));

        return datasetMetadata;
    }

    private DistributionMetadata addPropertiesToDistribution(JSONObject distribution,
            String uuid_distribution, String dataset_uri, JSONObject fdp, JSONObject dataset,
            JSONObject uploadConfiguration, String data) throws JSONException,
            InstantiationException, UnsupportedEncodingException, IllegalAccessException {

        String name = "FAIRdistribution_" + distribution.getString(TITLEPREDICATE).replace(" ", "_")
                + "_" + distribution.getString(VERSIONPREDICATE).replace(" ", "_");
        DistributionMetadata distributionMetadata = getMetadata(DistributionMetadata.class,
                distribution,
                fdp.getString("baseUri") + "/distribution/" + name + "_" + uuid_distribution);
        distributionMetadata
                .setMediaType(FACTORY.createLiteral(RDFFormat.TURTLE.getDefaultMIMEType()));
        distributionMetadata.setByteSize(FACTORY.createLiteral(data.getBytes("UTF-8").length));

        if (fdp.getString("uploadtype").equals("ftp")) {
            distributionMetadata.setDownloadURL(
                    FACTORY.createIRI("ftp://" + uploadConfiguration.getString("host")
                            + uploadConfiguration.getString("directory") + name + ".ttl"));
        } else if (fdp.getString("uploadtype").equals("virtuoso")) {
            // TODO hardcode url to something predefined
            // TODO or change to accessurl and point to virtuoso's sparql endpoint

            distributionMetadata
                    .setDownloadURL(FACTORY.createIRI(uploadConfiguration.getString("host")
                            + uploadConfiguration.getString("directory") + name + ".ttl"));
        }

        distributionMetadata.setParentURI(FACTORY.createIRI(dataset_uri));

        if (distribution.has(LICENSEPREDICATE)) {
            distributionMetadata
                    .setLicense(FACTORY.createIRI(distribution.getString(LICENSEPREDICATE)));
        }
        if (distribution.has(DESCRIPTIONPREDICATE)) {
            distributionMetadata.setDescription(
                    FACTORY.createLiteral(distribution.getString(DESCRIPTIONPREDICATE)));
        }
        return distributionMetadata;
    }

    private <T extends Metadata> T getMetadata(Class<T> type, JSONObject args, String uri)
            throws JSONException, InstantiationException, IllegalAccessException {
        T metadata = type.newInstance();
        metadata.setVersion(FACTORY.createLiteral(args.getString(VERSIONPREDICATE)));
        // TODO see if the generated uri is ignored when posting to fdp
        metadata.setUri(FACTORY.createIRI(uri));
        Date date = new Date();
        metadata.setIssued(FACTORY.createLiteral(date));
        metadata.setModified(FACTORY.createLiteral(date));
        metadata.setTitle(FACTORY.createLiteral(args.getString(TITLEPREDICATE)));
        if (args.has(RIGHTSPREDICATE)) {
            metadata.setRights(FACTORY.createIRI(args.getString(RIGHTSPREDICATE)));
        }
        if (!metadata.getClass().equals(DistributionMetadata.class)) {
            Agent agent = new Agent();
            agent.setUri(
                    FACTORY.createIRI(args.getJSONObject(PUBLISHERPREDICATE).getString("url")));
            agent.setName(
                    FACTORY.createLiteral(args.getJSONObject(PUBLISHERPREDICATE).getString("url")));
            metadata.setPublisher(agent);
        }
        return metadata;
    }

    private HttpResponse pushMetadataToFdp(String uuid, Metadata metadata, String metadataType,
            String baseUri) throws MetadataException, JSONException, IOException {

        String metadataLayerStr = MetadataUtils.getString(metadata, RDFFormat.TURTLE, false)
                .replaceAll("\\<" + metadata.getUri().toString() + "\\>", "<>");
        String metadataId = baseUri + "/" + metadataType + "?id="
                + metadata.getTitle().getLabel().replace(" ", "_") + "_"
                + metadata.getVersion().getLabel().replace(" ", "_") + "_" + uuid;

        return HttpUtils.post(metadataId, metadataLayerStr, RDFFormat.TURTLE.getDefaultMIMEType());
    }
}
