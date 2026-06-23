import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MiProveedorEducativo : MainAPI() {
    override var mainUrl = "https://cinemacity.cc"
    override var name = "CinemaCity Personal"
    override var lang = "es"
    override var hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Corrección de Claude: Usar newHomePageResponse moderno
        return newHomePageResponse(
            list = listOf(HomePageList("Destacados", emptyList())),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val urlBusqueda = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(urlBusqueda).document
        
        // Corrección de Claude: Evitar return null en mapNotNull, usar mapNotNull con condiciones
        return document.select("div.movie-item").mapNotNull { elemento ->
            val a = elemento.selectFirst("a") ?: return@mapNotNull null
            val titulo = elemento.selectFirst(".title")?.text() ?: return@mapNotNull null
            
            newMovieSearchResponse(titulo, fixUrl(a.attr("href")), TvType.Movie) {
                this.posterUrl = elemento.selectFirst("img")?.attr("src")
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
        val script = document.select("script").html()
        
        val regex = Regex("""file"[\s]*:[\s]*"([^"]+\.(mp4|m3u8)[^"]*)""")
        regex.findAll(script).forEach { match ->
            val url = match.groupValues[1]
            val esM3u8 = url.contains(".m3u8")
            
            // Corrección de Claude: Usar tipo de enlace explícito
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = url,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = if (esM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                )
            )
        }
        return true
    }
    
    // ... (Mantén tu función load aquí igual)
}
