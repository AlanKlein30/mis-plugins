import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class MiProveedorEducativo : MainAPI() {
    // ============================================================================
    // CONFIGURACIÓN BÁSICA DEL PROVEEDOR
    // Ajusta estos valores según la fuente que vayas a integrar
    // ============================================================================
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity Personal"
    override var lang = "es"
    override var hasMainPage = true

    // Define qué tipos de contenido ofrece este proveedor
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Others
    )

    // ============================================================================
    // PÁGINA PRINCIPAL (Opcional)
    // Se llama al abrir el proveedor en la app.
    // Retorna secciones con listas de contenido destacado.
    // ============================================================================
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        
        // Aquí harías una petición HTTP a la página principal
        // val document = app.get(mainUrl).document

        // Por ahora retornamos una lista vacía como placeholder
        val listaEjemplo = listOf<SearchResponse>()

        return newHomePageResponse(
            list = listOf(
                HomePageList(
                    name = "Contenido Destacado",
                    list = listaEjemplo,
                    isHorizontalImages = true
                )
            ),
            hasNext = false
        )
    }

    // ============================================================================
    // MÉTODO SEARCH
    // Recibe el término de búsqueda como String.
    // Retorna una lista de SearchResponse con los resultados.
    // ============================================================================
    override suspend fun search(query: String): List<SearchResponse> {
        // Codificamos el texto para que caracteres especiales (tildes, espacios, etc.) sean válidos en una URL
        val queryEncoded = URLEncoder.encode(query, "UTF-8")
        
        // Construimos la URL de búsqueda con el query codificado
        val urlBusqueda = "$mainUrl/buscar?q=$queryEncoded"
        
        // Realizamos la petición HTTP y parseamos el HTML con Jsoup
        val document = app.get(urlBusqueda).document
        
        // Seleccionamos todos los elementos que representan un resultado.
        // ADAPTAR: cambia "div.item-resultado" por el selector real del sitio.
        return document.select("div.item-resultado").mapNotNull { elemento ->
            parsearResultado(elemento)
        }
    }

    // FUNCTION AUXILIAR: parsear cada elemento de búsqueda
    // Extrae los datos de un elemento HTML individual.
    private fun parsearResultado(elemento: Element): SearchResponse? {
        return try {
            // Extraemos el enlace y título del resultado
            val enlaceElemento = elemento.selectFirst("a.titulo") ?: return null
            val titulo = enlaceElemento.text().trim()
            val urlRelativa = enlaceElemento.attr("href")
            
            // Construimos la URL absoluta si el href es relativo
            val urlCompleta = if (urlRelativa.startsWith("http")) {
                urlRelativa
            } else {
                "$mainUrl$urlRelativa"
            }
            
            // Extraemos la URL de la imagen de portada
            val imagenUrl = elemento.selectFirst("img.miniatura")
                ?.attr("data-src")
                ?.ifEmpty { elemento.selectFirst("img.miniatura")?.attr("src") }
            
            newMovieSearchResponse(
                name = titulo,
                url = urlCompleta,
                type = TvType.Movie
            ) {
                this.posterUrl = imagenUrl
            }
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================================
    // MÉTODO LOAD
    // Se llama al seleccionar un resultado de búsqueda.
    // Carga la página de detalle y retorna metadatos + episodios.
    // ============================================================================
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        // ADAPTAR: selectores reales de la página de detalle
        val titulo = document.selectFirst("h1.titulo-detalle")?.text()?.trim() ?: return null
        val descripcion = document.selectFirst("p.sinopsis")?.text()?.trim()
        val imagen = document.selectFirst("img.portada")?.attr("src")
        
        // Ejemplo para contenido tipo Película
        return newMovieLoadResponse(
            name = titulo,
            url = url,
            type = TvType.Movie,
            dataUrl = url
        ) {
            this.posterUrl = imagen
            this.plot = descripcion
        }
    }

    // ============================================================================
    // MÉTODO LOAD LINKS
    // Se llama al reproducir un ítem. Su objetivo es encontrar las URLs de los archivos de video (.mp4, .m3u8, etc.)
    // ============================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        val document = app.get(data).document
        
        // ESTRATEGIA 1: Buscar iframes con reproductores embebidos
        document.select("iframe").forEach { iframe ->
            val iframeSrc = iframe.attr("src").trim()
            if (iframeSrc.isNotEmpty()) {
                loadExtractor(
                    url = iframeSrc,
                    referer = data,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            }
        }
        
        // ESTRATEGIA 2: Buscar URLs directas .mp4 o .m3u8 en el HTML
        document.select("source[src], video[src]").forEach { elemento ->
            val videoUrl = elemento.attr("src").trim()
            if (videoUrl.contains(".mp4") || videoUrl.contains(".m3u8")) {
                
                val esM3u8 = videoUrl.contains(".m3u8")
                
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        referer = mainUrl,
                        quality = 1, // Corregido: Ahora pasa un entero válido directo
                        isM3u8 = esM3u8
                    )
                )
            }
        }
        
        // ESTRATEGIA 3: Extraer URL desde código JavaScript embebido
        val scriptContenido = document.select("script").joinToString("\n") { it.html() }
        val regexMp4 = Regex("""file"[\s]*:[\s]*"([^"]+\.mp4[^"]*)""")
        val regexM3u8 = Regex("""file"[\s]*:[\s]*"([^"]+\.m3u8[^"]*)""")
        
        regexMp4.findAll(scriptContenido).forEach { match ->
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name MP4",
                    url = match.groupValues[1],
                    referer = mainUrl,
                    quality = 1, // Corregido: Ahora pasa un entero válido directo
                    isM3u8 = false
                )
            )
        }
        
        regexM3u8.findAll(scriptContenido).forEach { match ->
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name V3U8",
                    url = match.groupValues[1],
                    referer = mainUrl,
                    quality = 1, // Corregido: Ahora pasa un entero válido directo
                    isM3u8 = true
                )
            )
        }
        
        return true
    }
}
