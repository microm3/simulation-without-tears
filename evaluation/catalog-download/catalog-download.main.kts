#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("com.google.code.gson:gson:2.11.0")
@file:DependsOn("org.apache.jena:jena-arq:5.1.0")

import com.google.gson.GsonBuilder
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr

// args[0] = path to curl_ontouml_catalog.ttl
// args[1] = output root (default: ./ontouml_downloads)

val CATALOG_TTL = File(args[0]).canonicalFile
val OUT_ROOT = if (args.size > 1) File(args[1]).canonicalFile else File("ontouml_downloads").canonicalFile
val DELAY_MS = 120L

val DCTERMS = "http://purl.org/dc/terms/"
val DCAT = "http://www.w3.org/ns/dcat#"
val RDFS = "http://www.w3.org/2000/01/rdf-schema#"
val FDP_O = "https://w3id.org/fdp/fdp-o#"
val OMODELS_VOC = "https://w3id.org/ontouml-models/vocabulary#"
val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
val gson = GsonBuilder().setPrettyPrinting().create()

data class ModelInfo(
    val iri: String,
    val issuedYear: String?,
    val distributions: List<String>,
    val storageUrl: String?,
    val label: String?,
    val title: String?,
    val issued: String?,
    val metadataIssued: String?,
    val metadataModified: String?,
    val language: String?,
    val license: String?,
    val source: String?,
    val catalogId: String?,
)

data class ManifestEntry(
    val modelIri: String, val slug: String, val issuedYear: String?, val label: String?,
    val dir: String, val json: String?,
)

fun slugify(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]+"), "_").trim('_').take(120).ifEmpty { "model" }

fun basenameFromUrl(url: String) = url.trimEnd('/').substringAfterLast('/').ifEmpty { "file" }

fun slugFromStorageUrl(url: String): String? {
    val last = URI.create(url).path.trimEnd('/').substringAfterLast('/')
    return if (last.isNotEmpty() && last != "models") slugify(last) else null
}

fun get(url: String, accept: String = "*/*"): String {
    val req = HttpRequest.newBuilder(URI.create(url))
        .header("Accept", accept)
        .timeout(Duration.ofSeconds(30))
        .build()
    val res = http.send(req, HttpResponse.BodyHandlers.ofString())
    check(res.statusCode() in 200..299) { "${res.statusCode()} GET $url" }
    return res.body()
}

fun download(url: String, dest: File): Boolean {
    dest.parentFile.mkdirs()
    val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build()
    val res = http.send(req, HttpResponse.BodyHandlers.ofByteArray())
    if (res.statusCode() !in 200..299) {
        System.err.println("SKIP ${res.statusCode()} GET $url")
        return false
    }
    dest.writeBytes(res.body())
    return true
}

fun parseTurtle(text: String) = ModelFactory.createDefaultModel().also {
    RDFDataMgr.read(it, text.byteInputStream(), Lang.TURTLE)
}

// Parse catalog TTL into a Jena model, then list every `dcat:Dataset` and its metadata / distribution IRIs
fun extractModels(text: String): List<ModelInfo> {
    val rdf = parseTurtle(text)
    val query = QueryFactory.create(
        """
        PREFIX dcat: <${DCAT}>
        PREFIX dct: <${DCTERMS}>
        PREFIX rdfs: <${RDFS}>
        PREFIX fdp: <${FDP_O}>
        PREFIX ov: <${OMODELS_VOC}>
        
        SELECT
            ?model ?issued ?storageUrl ?label ?title
            ?metadataIssued ?metadataModified ?language ?license ?source ?catalogId
            ?distribution
        WHERE {
            ?model a dcat:Dataset .
            OPTIONAL { ?model dct:issued ?issued . }
            OPTIONAL { ?model ov:storageUrl ?storageUrl . }
            OPTIONAL { ?model rdfs:label ?label . }
            OPTIONAL { ?model dct:title ?title . }
            OPTIONAL { ?model fdp:metadataIssued ?metadataIssued . }
            OPTIONAL { ?model fdp:metadataModified ?metadataModified . }
            OPTIONAL { ?model dct:language ?language . }
            OPTIONAL { ?model dct:license ?license . }
            OPTIONAL { ?model dct:source ?source . }
            OPTIONAL { ?model dct:isPartOf ?catalogId . }
            OPTIONAL { ?model dcat:distribution ?distribution . }
        }
        """.trimIndent()
    )

    data class ModelAccumulator(
        val iri: String,
        val issued: String?,
        val storageUrl: String?,
        val label: String?,
        val title: String?,
        val metadataIssued: String?,
        val metadataModified: String?,
        val language: String?,
        val license: String?,
        val source: String?,
        val catalogId: String?,
        val distributions: MutableSet<String> = linkedSetOf(),
    )

    return QueryExecutionFactory.create(query, rdf).use { qe ->
        val byIri = linkedMapOf<String, ModelAccumulator>()
        qe.execSelect().asSequence().forEach { row ->
            val iri = row.getResource("model").uri
            val issued = row.get("issued")?.let { node ->
                if (node.isLiteral) node.asLiteral().string else node.toString()
            }
            val current = byIri.getOrPut(iri) {
                ModelAccumulator(
                    iri = iri,
                    issued = issued,
                    storageUrl = row.get("storageUrl")?.takeIf { it.isLiteral }?.asLiteral()?.string,
                    label = row.get("label")?.takeIf { it.isLiteral }?.asLiteral()?.string,
                    title = row.get("title")?.takeIf { it.isLiteral }?.asLiteral()?.string,
                    metadataIssued = row.get("metadataIssued")?.takeIf { it.isLiteral }?.asLiteral()?.string,
                    metadataModified = row.get("metadataModified")?.takeIf { it.isLiteral }?.asLiteral()?.string,
                    language = row.get("language")?.takeIf { it.isLiteral }?.asLiteral()?.string,
                    license = row.get("license")?.takeIf { it.isResource }?.asResource()?.uri,
                    source = row.get("source")?.takeIf { it.isResource }?.asResource()?.uri,
                    catalogId = row.get("catalogId")?.takeIf { it.isResource }?.asResource()?.uri,
                )
            }
            val distribution = row.get("distribution")?.takeIf { it.isResource }?.asResource()?.uri
            if (distribution != null) current.distributions.add(distribution)
        }

        byIri.values.map { acc ->
            ModelInfo(
                iri = acc.iri,
                issuedYear = acc.issued,
                distributions = acc.distributions.toList(),
                storageUrl = acc.storageUrl,
                label = acc.label,
                title = acc.title,
                issued = acc.issued,
                metadataIssued = acc.metadataIssued,
                metadataModified = acc.metadataModified,
                language = acc.language,
                license = acc.license,
                source = acc.source,
                catalogId = acc.catalogId,
            )
        }
    }
}

fun jsonDistributionDownloadUrl(ttl: String): String? {
    val rdf = parseTurtle(ttl)
    val query = QueryFactory.create(
        """
        PREFIX dcat: <${DCAT}>
        
        SELECT ?download ?mediaType
        WHERE {
            ?distribution dcat:downloadURL ?download .
            OPTIONAL { ?distribution dcat:mediaType ?mediaType . }
        }
        LIMIT 1
        """.trimIndent()
    )
    val (download, mediaIri) = QueryExecutionFactory.create(query, rdf).use { qe ->
        val row = qe.execSelect().asSequence().firstOrNull() ?: return null
        val downloadUri = row.get("download")?.takeIf { it.isResource }?.asResource()?.uri ?: return null
        val media = row.get("mediaType")?.let { node ->
            when {
                node.isResource -> node.asResource().uri
                node.isLiteral -> node.asLiteral().string
                else -> null
            }
        } ?: return null
        downloadUri to media
    }
    val m = mediaIri.lowercase()
    if (!m.endsWith("application/json") && !m.contains("/application/json")) return null
    return download
}

// main
val models = extractModels(CATALOG_TTL.readText())
println("${models.size} models")
OUT_ROOT.mkdirs()

val entries = mutableListOf<ManifestEntry>()

for (model in models) {
    val slug = model.storageUrl?.let { slugFromStorageUrl(it) }
        ?: slugify(model.label ?: model.iri.takeLast(36))
    val modelDir = OUT_ROOT.resolve(slug)

    val jsonDownloads = mutableListOf<String>()
    for (distIri in model.distributions) {
        Thread.sleep(DELAY_MS)
        val ttl = try {
            get(distIri, "text/turtle, application/n-triples;q=0.9, */*;q=0.1")
        } catch (e: Exception) {
            System.err.println("Skip distribution $distIri: ${e.message}")
            continue
        }
        jsonDistributionDownloadUrl(ttl)?.let { jsonDownloads.add(it) }
    }

    val primaryJsonUrl = jsonDownloads.find { it.endsWith("ontology.json", ignoreCase = true) }
        ?: jsonDownloads.firstOrNull()

    if (primaryJsonUrl == null) {
        println("$slug  SKIP (no json)")
        continue
    }

    val entry = ManifestEntry(model.iri, slug, model.issuedYear, model.label,
        dir = modelDir.relativeTo(OUT_ROOT).path,
        json = null)
    modelDir.mkdirs()

    val modelMetadata = linkedMapOf(
        "modelIri" to model.iri,
        "catalogModelPage" to model.iri,
        "name" to (model.title ?: model.label),
        "issued" to model.issued,
        "metadataIssued" to model.metadataIssued,
        "metadataModified" to model.metadataModified,
        "language" to model.language,
        "license" to model.license,
        "source" to model.source,
        "catalogId" to model.catalogId,
    ).filterValues { it != null }
    modelDir.resolve("model_metadata.json").writeText(gson.toJson(modelMetadata))

    val jsonName = basenameFromUrl(primaryJsonUrl)
    Thread.sleep(DELAY_MS)
    val jsonOk = download(primaryJsonUrl, modelDir.resolve(jsonName))
    val resolvedJsonName = if (jsonOk) jsonName else null

    entries.add(entry.copy(json = resolvedJsonName))
    println("$slug  (json=${resolvedJsonName ?: "FAILED"})")
}

val manifest = mapOf(
    "generatedAt" to java.time.Instant.now().toString(),
    "models" to entries,
)

val manifestFile = OUT_ROOT.resolve("manifest.json")
manifestFile.writeText(gson.toJson(manifest))
println("Done downloading OntoUML Catalog models.")
