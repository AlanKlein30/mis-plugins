import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class MiProveedorEducativo : MainAPI() {
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity Personal"
    override var lang = "es"
    override var hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
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

    override suspend fun search(query: String): List<SearchResponse> {
        val queryEncoded = URLEncoder.encode(query, "UTF-8")
        // El sitio usa un sistema dinámico, usamos una ruta de búsqueda compatible básica
        val urlBusqueda = "$mainUrl/?s=$queryEncoded"
        val document = app.get(urlBusqueda).document
        
        // Selectores básicos para listar películas en el catálogo
        return document.select("div.movie-item, div.poster").mapNotNull { elemento ->
            val enlaceElemento = elemento.selectFirst("a") ?: return null
            val titulo = elemento.selectFirst(".title, h2, h3")?.text()?.trim() ?: "Sin título"
            val urlRelativa = enlaceElemento.attr("href")
            val urlCompleta = fixUrl(urlRelativa)
            val imagenUrl = elemento.selectFirst("img")?.attr("src")

            val tipo = if (urlCompleta.contains("/tv-series/")) TvType.TvSeries else TvType.Movie

            newMovieSearchResponse(titulo, urlCompleta, tipo) {
                this.posterUrl = imagenUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val titulo = document.selectFirst("h1, .movie-title")?.text()?.trim() ?: return null
        val descripcion = document.selectFirst(".plot, .description, p")?.text()?.trim()
        val imagen = document.selectFirst("img.poster, .movie-poster img")?.attr("src")
        
        return if (url.contains("/tv-series/")) {
            // Estructura básica para Series
            val episodios = mutableListOf<Episode>()
            document.select(".episode-item, a[href*='/episode/']").forEach { item ->
                val epUrl = fixUrl(item.attr("href"))
                val epName = item.text().trim()
                episodios.add(Episode(epUrl, epName))
            }
            newTvSeriesLoadResponse(titulo, url, TvType.TvSeries, episodios) {
                this.posterUrl = imagen
                this.plot = descripcion
            }
        } else {
            // Estructura para Películas
            newMovieLoadResponse(titulo, url, TvType.Movie, url) {
                this.posterUrl = imagen
                this.plot = descripcion
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // ESTRATEGIA 1: Iframes embebidos
        document.select("iframe").forEach { iframe ->
            val iframeSrc = iframe.attr("src").trim()
            if (iframeSrc.isNotEmpty()) {
                loadExtractor(iframeSrc, data, subtitleCallback, callback)
            }
        }
        
        // ESTRATEGIA 2: Código JavaScript (reemplazado por el nuevo formato estructurado)
        val scriptContenido = document.select("script").joinToString("\n") { it.html() }
        val regexMp4 = Regex("""file"[\s]*:[\s]*"([^"]+\.mp4[^"]*)""")
        val regexM3u8 = Regex("""file"[\s]*:[\s]*"([^"]+\.m3u8[^"]*)""")
        
        regexMp4.findAll(scriptContenido).forEach { match ->
            callback.invoke(
                ExtractorLink(
                    name,
                    "$name MP4",
                    match.groupValues[1],
                    mainUrl,
                    Qualities.Unknown.value,
                    false
                )
            )
        }
        
        regexM3u8.findAll(scriptContenido).forEach { match ->
            callback.invoke(
                ExtractorLink(
                    name,
                    "$name M3U8",
                    match.groupValues[1],
                    mainUrl,
                    Qualities.Unknown.value,
                    true
                )
            )
        }
        
        return true
    }
}
