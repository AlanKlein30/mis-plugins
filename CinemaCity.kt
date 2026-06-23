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
        // Corrección: Retornamos una lista vacía mutable válida para la app
        val listaEjemplo = mutableListOf<SearchResponse>()
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
        val urlBusqueda = "$mainUrl/?s=$queryEncoded"
        val document = app.get(urlBusqueda).document
        
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
            val episodios = mutableListOf<Episode>()
            document.select(".episode-item, a[href*='/episode/']").forEach { item ->
                val epUrl = fixUrl(item.attr("href"))
                val epName = item.text().trim()
                // Corrección: Usamos el método moderno exigido por la app
                val nuevoEpisodio = newEpisode(epUrl) {
                    this.name = epName
                }
                episodios.add(nuevoEpisodio)
            }
            newTvSeriesLoadResponse(titulo, url, TvType.TvSeries, episodios) {
                this.posterUrl = imagen
                this.plot = descripcion
            }
        } else {
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
        
        document.select("iframe").forEach { iframe ->
            val iframeSrc = iframe.attr("src").trim()
            if (iframeSrc.isNotEmpty()) {
                loadExtractor(iframeSrc, data, subtitleCallback, callback)
            }
        }
        
        val scriptContenido = document.select("script").joinToString("\n") { it.html() }
        val regexMp4 = Regex("""file"[\s]*:[\s]*"([^"]+\.mp4[^"]*)""")
        val regexM3u8 = Regex("""file"[\s]*:[\s]*"([^"]+\.m3u8[^"]*)""")
        
        regexMp4.findAll(scriptContenido).forEach { match ->
            // Corrección: Cambiado al constructor estructurado moderno obligatorio
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name MP4",
                    url = match.groupValues[1],
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }
        
        regexM3u8.findAll(scriptContenido).forEach { match ->
            // Corrección: Cambiado al constructor estructurado moderno obligatorio
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name M3U8",
                    url = match.groupValues[1],
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        }
        
        return true
    }
}
