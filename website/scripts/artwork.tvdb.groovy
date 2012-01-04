// filebot -script "http://filebot.sf.net/scripts/artwork.tvdb.groovy" -trust-script /path/to/media/

// EXPERIMENTAL // HERE THERE BE DRAGONS
if (net.sourceforge.filebot.Settings.applicationRevisionNumber < 838) throw new Exception("Application revision too old")

/*
 * Fetch series and season banners for all tv shows. Series name is auto-detected if possible or the folder name is used.
 */

def fetchBanner(outputFile, series, bannerType, bannerType2 = null, season = null) {
	// select and fetch banner
	def banner = ['en', null].findResult { TheTVDB.getBanner(series, [BannerType:bannerType, BannerType2:bannerType2, Season:season, Language:it]) }
	if (banner == null) {
		println "Banner not found: $outputFile / $bannerType:$bannerType2"
		return null
	}
	
	println "Fetching $outputFile => $banner"
	return banner.url.saveAs(outputFile)
}


def fetchNfo(outputFile, series) {
	def info = TheTVDB.getSeriesInfo(series, _args.locale)
	println info  
	info.applyXmlTemplate('''<tvshow xmlns:gsp='http://groovy.codehaus.org/2005/gsp'>
			<title>$name</title>
			<year>$firstAired.year</year>
			<rating>$rating</rating>
			<votes>$ratingCount</votes>
			<plot>$overview</plot>
			<runtime>$runtime</runtime>
			<mpaa>$contentRating</mpaa>
			<genre>${!genres.empty ? genres[0] : ''}</genre>
			<id>$id</id>
			<thumb>$bannerUrl</thumb>
			<premiered>$firstAired</premiered>
			<status>$status</status>
			<studio>$network</studio>
			<gsp:scriptlet> actors.each { </gsp:scriptlet>
				<actor>
	    			<name>$it</name>
				</actor>
			<gsp:scriptlet> } </gsp:scriptlet>
		</tvshow>
	''').saveAs(outputFile)
}


def fetchSeriesBannersAndNfo(seriesDir, seasonDir, series, season) {
	println "Fetch nfo and banners for $series / Season $season"
	
	TheTVDB.getBannerList(series).each {
		println "Available banner: $it.url => $it"
	}
	
	// fetch nfo
	fetchNfo(seriesDir['tvshow.nfo'], series)
			
	// fetch series banner, fanart, posters, etc
	["680x1000",  null].findResult{ fetchBanner(seriesDir['folder.jpg'], series, "poster", it) }
	["graphical", null].findResult{ fetchBanner(seriesDir['banner.jpg'], series, "series", it) }
	
	// fetch highest resolution fanart
	["1920x1080", "1280x720", null].findResult{ fetchBanner(seriesDir["fanart.jpg"], series, "fanart", it) }
	
	// fetch season banners
	if (seasonDir != seriesDir) {
		fetchBanner(seasonDir["folder.jpg"], series, "season", "season", season)
		fetchBanner(seasonDir["banner.jpg"], series, "season", "seasonwide", season)
	}
}


args.eachMediaFolder { dir ->
	def videos = dir.listFiles{ it.isVideo() }
	
	def query = _args.query ?: detectSeriesName(videos, _args.locale)
	def sxe = videos.findResult{ parseEpisodeNumber(it) }
	
	if (query == null) {
		query = dir.dir.hasFile{ it.name =~ /Season/ && it.isDirectory() } ? dir.dir.name : dir.name
	}
	
	println "$dir => Search by $query"
	def options = TheTVDB.search(query, _args.locale)
	if (options.isEmpty()) {
		println "TV Series not found: $query"
		return null
	}
	
	// sort by relevance
	options = options.sortBySimilarity(query, { it.name })
	
	// auto-select series
	def series = options[0]
	
	// maybe require user input
	if (options.size() != 1 && !_args.nonStrict && !java.awt.GraphicsEnvironment.headless) {
		series = javax.swing.JOptionPane.showInputDialog(null, "Please select TV Show:", dir.path, 3, null, options.toArray(), series);
		if (series == null) return null
	}
	
	// auto-detect structure
	def seriesDir = [dir.dir, dir].sortBySimilarity(series.name, { it.name })[0]
	def season = sxe && sxe.season > 0 ? sxe.season : 1
	
	println "$dir => $series"
	fetchSeriesBannersAndNfo(seriesDir, dir, series, season)
}
