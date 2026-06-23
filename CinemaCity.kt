import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class MiProveedorEducativo : MainAPI() {

    // =========================================================
    // CONFIGURACIÓN BÁSICA DEL PROVEEDOR
    // Ajusta estos valores según la fuente que vayas a integrar
    // =========================================================
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity Personal"
    override var lang = "es"
    override val hasMainPage = true
    override val hasSearch = true

    // Define qué tipos de contenido ofrece este proveedor
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Others
    )

    // =========================================================
    // PÁGINA PRINCIPAL (opcional)
    // Se llama al abrir el proveedor en la app.
    // Retorna secciones con listas de contenido destacado.
    // =========================================================
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

    // =========================================================
    // MÉTODO SEARCH
    // Recibe el término de búsqueda como String.
    // Retorna una lista de SearchResponse con los resultados.
    //
    // CÓMO ADAPTAR:
    //   1. Reemplaza la URL por la URL real de búsqueda del sitio.
    //   2. Ajusta el selector CSS al elemento HTML que envuelve
    //      cada resultado (ej: "div.result-item", "article.card").
    //   3. Extrae: título, url, imagen y tipo de cada elemento.
    // =========================================================
    override suspend fun search(query: String): List<SearchResponse> {

        // Codificamos el texto para que caracteres especiales (tildes,
        // espacios, ñ, etc.) sean válidos en una URL
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

    // =========================================================
    // FUNCIÓN AUXILIAR: parsear cada elemento de búsqueda
    // Extrae los datos de un elemento HTML individual.
    //
    // CÓMO ADAPTAR:
    //   - "a.titulo"       → selector del enlace/título
    //   - "img.miniatura"  → selector de la imagen de portada
    //   - Ajusta TvType según el contenido (Movie, TvSeries, etc.)
    // =========================================================
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

            // Extraemos la URL de la imagen de portada (puede ser attr "src" o "data-src")
            val imagenUrl = elemento.selectFirst("img.miniatura")
                ?.attr("data-src")
                ?.ifEmpty { elemento.selectFirst("img.miniatura")?.attr("src") }

            // Retornamos un objeto SearchResponse con los datos extraídos
            // newMovieSearchResponse → para películas / contenido único
            // newAnimeSearchResponse → para anime
            // newTvSeriesSearchResponse → para series con episodios
            newMovieSearchResponse(
                name = titulo,
                url = urlCompleta,
                type = TvType.Movie
            ) {
                this.posterUrl = imagenUrl
            }

        } catch (e: Exception) {
            // Si un elemento falla, lo ignoramos y continuamos con los demás
            null
        }
    }

    // =========================================================
    // MÉTODO LOAD
    // Se llama al seleccionar un resultado de búsqueda.
    // Carga la página de detalle y retorna metadata + episodios.
    //
    // CÓMO ADAPTAR:
    //   1. Parsea título, descripción, imagen de la página de detalle.
    //   2. Para series: recorre episodios y construye una lista de Episode.
    //   3. Para películas: retorna directamente newMovieLoadResponse.
    // =========================================================
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // ADAPTAR: selectores reales de la página de detalle
        val titulo = document.selectFirst("h1.titulo-detalle")?.text()?.trim()
            ?: return null
        val descripcion = document.selectFirst("p.sinopsis")?.text()?.trim()
        val imagen = document.selectFirst("img.portada")?.attr("src")

        // Ejemplo para contenido tipo Película
        return newMovieLoadResponse(
            name = titulo,
            url = url,
            type = TvType.Movie,
            // dataUrl: la URL desde donde loadLinks extraerá los enlaces.
            // Puede ser la misma URL u otra URL del reproductor embebido.
            dataUrl = url
        ) {
            this.posterUrl = imagen
            this.plot = descripcion
        }

        // -------------------------------------------------------
        // ALTERNATIVA para Series con episodios:
        // -------------------------------------------------------
        // val episodios = document.select("ul.lista-episodios li").mapIndexed { index, ep ->
        //     val epUrl = ep.selectFirst("a")?.attr("href") ?: return@mapIndexed null
        //     val epTitulo = ep.selectFirst("span.nombre")?.text() ?: "Episodio ${index + 1}"
        //     Episode(
        //         data = if (epUrl.startsWith("http")) epUrl else "$mainUrl$epUrl",
        //         name = epTitulo,
        //         episode = index + 1
        //     )
        // }.filterNotNull()
        //
        // return newTvSeriesLoadResponse(
        //     name = titulo,
        //     url = url,
        //     type = TvType.TvSeries,
        //     episodes = episodios
        // ) {
        //     this.posterUrl = imagen
        //     this.plot = descripcion
        // }
    }

    // =========================================================
    // MÉTODO LOAD LINKS
    // Se llama al reproducir un ítem. Su objetivo es encontrar
    // las URLs de los archivos de video (.mp4, .m3u8, etc.)
    // y registrarlas mediante el callback subtitleCallback / callback.
    //
    // PARÁMETROS:
    //   data          → URL de la página del reproductor
    //   isCasting     → true si se está haciendo cast a otro dispositivo
    //   subtitleCallback → para registrar subtítulos encontrados
    //   callback      → función que recibe cada enlace de video encontrado
    //
    // CÓMO ADAPTAR:
    //   1. Carga la página del reproductor con app.get(data).
    //   2. Busca iframes con src que apunten a un reproductor externo.
    //   3. O extrae directamente URLs .mp4 / .m3u8 del HTML o del JS.
    //   4. Llama a loadExtractor() para reproductores conocidos,
    //      o construye un ExtractorLink manual para URLs directas.
    // =========================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        // ----------------------------------------------------------
        // ESTRATEGIA 1: Buscar iframes con reproductores embebidos
        // loadExtractor() reconoce automáticamente reproductores
        // populares (Doodstream, Streamtape, Voe, etc.)
        // ----------------------------------------------------------
        document.select("iframe").forEach { iframe ->
            val iframeSrc = iframe.attr("src").trim()
            if (iframeSrc.isNotEmpty()) {
                // loadExtractor intenta extraer el video del reproductor externo
                loadExtractor(
                    url = iframeSrc,
                    referer = data,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            }
        }

        // ----------------------------------------------------------
        // ESTRATEGIA 2: Buscar URLs directas .mp4 o .m3u8 en el HTML
        // Útil cuando el sitio expone la URL de video directamente.
        //
        // CÓMO ADAPTAR:
        //   - Ajusta el selector al elemento <source>, <video> o
        //     al atributo data-* donde esté la URL del video.
        // ----------------------------------------------------------
        document.select("source[src], video[src]").forEach { elemento ->
            val videoUrl = elemento.attr("src").trim()
            if (videoUrl.contains(".mp4") || videoUrl.contains(".m3u8")) {

                // Determinamos el tipo de stream
                val esM3U8 = videoUrl.contains(".m3u8")

                callback.invoke(
                    ExtractorLink(
                        source = name,              // Nombre del proveedor
                        name = name,                // Etiqueta visible en la app
                        url = videoUrl,             // URL directa del video
                        referer = mainUrl,          // Referer HTTP necesario para algunas fuentes
                        quality = Qualities.Unknown, // O: Qualities.P1080, P720, P480, P360
                        isM3u8 = esM3U8             // true para HLS/M3U8, false para MP4
                    )
                )
            }
        }

        // ----------------------------------------------------------
        // ESTRATEGIA 3: Extraer URL desde código JavaScript embebido
        // Útil cuando la URL está ofuscada o en una variable JS.
        //
        // CÓMO ADAPTAR:
        //   - Ajusta la regex al patrón real que usa el sitio.
        //   - Ejemplo: file:"https://cdn.ejemplo.com/video.mp4"
        // ----------------------------------------------------------
        val scriptContenido = document.select("script").joinToString("\n") { it.html() }
        val regexMp4 = Regex("""file["\s]*:["\s]*["'](https?://[^"']+\.mp4[^"']*)["']""")
        val regexM3u8 = Regex("""file["\s]*:["\s]*["'](https?://[^"']+\.m3u8[^"']*)["']""")

        regexMp4.findAll(scriptContenido).forEach { match ->
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name MP4",
                    url = match.groupValues[1],
                    referer = mainUrl,
                    quality = Qualities.Unknown,
                    isM3u8 = false
                )
            )
        }

        regexM3u8.findAll(scriptContenido).forEach { match ->
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name M3U8",
                    url = match.groupValues[1],
                    referer = mainUrl,
                    quality = Qualities.Unknown,
                    isM3u8 = true
                )
            )
        }

        // Retorna true si se intentó extraer (independientemente del resultado)
        return true
    }
}
