$ ->
  #
  # Enum-equivalents, singletons
  #
  SearchState =
    Compendium: 1
    Favourites: 4
    Interactions: 2
    Prescriptions: 3

  SearchType =
    Title: 1
    Owner: 2
    Atc: 3
    Regnr: 4
    Therapie: 5
    FullText: 6
    # Invisible search type for favourites initialisation
    Regnrs: 7
    FullTextHashes: 8

  #
  # Functions
  #
  getSearchTypeStr = (type) ->
    if type == SearchType.Title
      return "title"
    else if type == SearchType.Owner
      return "author"
    else if type == SearchType.Atc
      return "atc"
    else if type == SearchType.Regnr
      return "regnr"
    else if type == SearchType.Therapie
      return "therapy"
    else if type == SearchType.FullText
      return "fulltext"

  disableButton = (type) ->
    if type == SearchType.Title
      $('#article-button').removeClass('active')
    else if type == SearchType.Owner
      $('#owner-button').removeClass('active')
    else if type == SearchType.Atc
      $('#substance-button').removeClass('active')
    else if type == SearchType.Regnr
      $('#regnr-button').removeClass('active')
    else if type == SearchType.Therapie
      $('#therapy-button').removeClass('active')
    else if type == SearchType.FullText
      $('#fulltext-button').removeClass('active')

  setSearchQuery = (lang, type) ->
    if type == SearchType.Title
      return '/name?lang=' + lang + '&name='
    else if type == SearchType.Owner
      return '/owner?lang=' + lang + '&owner='
    else if type == SearchType.Atc
      return '/atc?lang=' + lang + '&atc='
    else if type == SearchType.Regnr
      return '/regnr?lang=' + lang + '&regnr='
    else if type == SearchType.Therapie
      return '/therapy?lang=' + lang + '&therapy='
    else if type == SearchType.FullText
      return '/fulltext?lang=' + lang + '&key='
    else if type == SearchType.Regnrs
      return '/regnrs?lang=' + lang + '&regnrs='
    else if type == SearchType.FullTextHashes
      return '/fulltext_hash?lang=' + lang + '&hashes='
    return '/name?lang=' + lang + '&name='

  getParams = ->
    # Usage: getSearchTypeInt getParams()['type']
    query = window.location.search.substring(1)
    raw_vars = query.split("&")
    params = {}
    for v in raw_vars
      [key, val] = v.split("=")
      params[key] = decodeURIComponent(val)
    return params


  #
  # Local storage handlers
  #
  # set language
  if localStorage.getItem 'language'
    language = (String) localStorage.getItem 'language'
  else
    language = 'de'   # default language
    localStorage.setItem 'language', language

  # set search state
  if localStorage.getItem 'search-state'
    search_state = (Number) localStorage.getItem 'search-state'
    # make sure to toggle the activity state of the nav-button
    if search_state == SearchState.Compendium
      $('#compendium-button').toggleClass 'nav-button-active'
    else if search_state == SearchState.Favourites
      $('#favourites-button').toggleClass 'nav-button-active'
    else if search_state == SearchState.Interactions
      $('#interactions-button').toggleClass 'nav-button-active'
      $('#fulltext-button').disabled = 'disabled'
    else if search_state == SearchState.Prescriptions
      $('#prescriptions-button').toggleClass 'nav-button-active'
      $('#fulltext-button').disabled = 'disabled'
  else
    search_state = SearchState.Compendium  # default search state is 'compendium'
    $('#compendium-button').toggleClass 'nav-button-active'
    localStorage.setItem 'search-state', search_state

  # set search type
  if localStorage.getItem 'search-type'
    search_type = (Number) localStorage.getItem 'search-type'
  else
    search_type = SearchType.Title   # default search type is 'article'
    localStorage.setItem 'search-type', search_type

  # set interactions basket
  if localStorage.getItem 'interactions-basket'
    interactions_basket = (String) localStorage.getItem 'interactions-basket'
  else
    interactions_basket = ''  # default interactions basket is empty!
    localStorage.setItem 'interactions-basket', interactions_basket
  # max URI length is 2083, we limit at 90 x (13+1) = 1260
  if interactions_basket.length > 1260
    interactions_basket = ''  # default interactions basket is empty!
    localStorage.setItem 'interactions-basket', interactions_basket

  #
  # Variables
  #
  # contains whatever we type into the search field
  typed_input = ''
  # default value
  search_query = setSearchQuery(language, search_type)

  start_time = new Date().getTime()

  articles = new Bloodhound(
    datumTokenizer: Bloodhound.tokenizers.obj.whitespace('name')
    queryTokenizer: Bloodhound.tokenizers.whitespace
    remote:
      wildcard: '%QUERY'
      url: search_query
      # This is where the magic happens:
      # retrieve information from client through a GET request
      replace: (url, query) ->
        return search_query + query
      filter: (list) ->
        if search_state == SearchState.Favourites
          if search_type == SearchType.FullText || search_type == SearchType.FullTextHashes
            list = list.filter((m)-> Favourites.cachedFullText.has(m.hash))
          else
            list = list.filter((m)-> Favourites.cached.has(m.regnrs))

        # Sets the number of results in search field (on the right)
        document.getElementById('num-results').textContent=list.length
        return list
  )

  # kicks off the loading/processing of "remote" and "prefetch"
  articles.initialize()

  typeaheadCtrl = $('#input-form .twitter-typeahead')

  packInfoDataForPrescriptionBasket = (data, packinfo) ->
    JSON.stringify({
      eancode: data.eancode,
      package: packinfo.title,
      title: data.title
      author: data.author,
      regnrs: data.regnrs,
      atccode: data.atccode
    })

  atcCodeElement = (data)->
    if !data.atccode
      return ""
    atcCodeStr = ""
    atcTitleStr = ""
    mCode = data.atccode.split(';')
    if mCode.length > 1
      atcCodeStr = mCode[0]
      atcTitleStr = mCode[1]

    atcCodeStr = atcCodeStr.split(',').map((code)-> "<a class='atc-code' href='/?atc_query=#{code}'>#{code}</a>")

    if data.atcclass
      mClass = data.atcclass.split(';')
      if mClass.length == 1
        atcCodeStr = "<p>" + atcCodeStr + " - " + atcTitleStr + "</p><p>" + mClass[0] + "</p>"
      else if mClass.length == 2 # *** Ver.<1.2.4
        atcCodeStr = "<p>" + atcCodeStr + " - " + atcTitleStr + "</p><p>" + mClass[1] + "</p>"
      else if mClass.length == 3 # *** Ver. 1.2.4 and above
        atcClassL4AndL5 = mClass[2].split('#')
        atcClassStr = ""
        if atcClassL4AndL5.length
          atcClassStr = atcClassL4AndL5[atcClassL4AndL5.length - 1];
        atcCodeStr = "<p>" + atcCodeStr + " - " + atcTitleStr + "</p><p>" + atcClassStr + "</p><p>" + mClass[1] + "</p>"
    else
      atcCodeStr = "<p>" + atcCodeStr + " - " + atcTitleStr + "</p><p>k.A.</p>";
    return atcCodeStr

  sourceWithFavDefaults = (query, sync, async)->
    if query == ''
      if search_state == SearchState.Favourites
        if search_type == SearchType.FullText || search_type == SearchType.FullTextHashes
          Favourites.getFullTextHashes().then (hashes)->
            hashesStr = hashes.join(',')
            search_type = SearchType.FullTextHashes
            search_query = setSearchQuery(language, search_type)
            articles.search(hashesStr, sync, async)
            search_type = SearchType.FullText
            search_query = setSearchQuery(language, search_type)
        else
          original_search_type = search_type
          Favourites.getRegNrs().then (regnrs)->
            regnrsStr = regnrs.join(',').split(',').filter((x)->x.length).join(',')
            search_type = SearchType.Regnrs
            search_query = setSearchQuery(language, search_type)
            articles.search(regnrsStr, sync, async)
            search_type = original_search_type
            search_query = setSearchQuery(language, search_type)
      else
        # Not fav and no query = no result
        sync([])
    else
      articles.search(query, sync, async)
  typeaheadCtrl.typeahead
    menu: $('#special-dropdown')
    hint: false
    highlight: false
    minLength: 0
  ,
    name: 'articles'
    displayKey: 'name'
    limit: '300'
    source: sourceWithFavDefaults
    templates:
      suggestion: (data) ->
        favouritesButton = '<div class="add-favourites-button ' + (if Favourites.cached.has(data.regnrs) then '--favourited' else '') + '" data-regnrs="' + data.regnrs + '"></div>'
        if search_type == SearchType.Title
          packsStr = (packinfo)->
            "<p class='article-packinfo' style='color:#{packinfo.color};' data-prescription='#{packInfoDataForPrescriptionBasket(data, packinfo)}'>
              #{packinfo.title}
            </p>"
          "<div style='display:table;vertical-align:middle;'>" + favouritesButton + "\
          <p style='color:var(--text-color-light);font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='font-size:0.85em;'>#{data.packinfos.map(packsStr).join('')}</span></div>"
        else if search_type == SearchType.Owner
          "<div style='display:table;vertical-align:middle;' class='typeahead-suggestion-wrapper'>" + favouritesButton +
          "<p style='color:var(--text-color-light);font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:#8888cc;font-size:1.0em;'><p>#{data.author}</p></span></div>"
        else if search_type == SearchType.Atc
          "<div style='display:table;vertical-align:middle;' class='typeahead-suggestion-wrapper'>" + favouritesButton +
          "<p style='color:var(--text-color-light);font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:gray;font-size:0.85em;'>#{atcCodeElement(data)}</span></div>"
        else if search_type == SearchType.Regnr || search_type == SearchType.Regnrs
          "<div style='display:table;vertical-align:middle;' class='typeahead-suggestion-wrapper'>" + favouritesButton +
          "<p style='color:var(--text-color-light);font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:#8888cc;font-size:1.0em;'><p>#{data.regnrs}</p></span></div>"
        else if search_type == SearchType.Therapie
          "<div style='display:table;vertical-align:middle;' class='typeahead-suggestion-wrapper'>" + favouritesButton +
          "<p style='color:var(--text-color-light);font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:gray;font-size:0.85em;'>#{data.therapy}</span></div>"
        else if search_type == SearchType.FullText || search_type == SearchType.FullTextHashes
          favFTButton = '<div class="add-favourites-button --full-text' +
            (if Favourites.cachedFullText.has(data.hash) then ' --favourited' else '') +
            '" data-hash="' + data.hash + '"></div>'
          "<div style='display:table;vertical-align:middle;' class='typeahead-suggestion-wrapper'>" + favFTButton +
          "<p style='color:var(--text-color-light);font-size:1.0em;'><b>#{data.title}</b></p>"

  typeaheadCtrl.on 'typeahead:asyncrequest', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    start_time = new Date().getTime()

  typeaheadCtrl.on 'typeahead:asyncreceive', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    request_time = new Date().getTime() - start_time  # request time in [ms]

    $('.atc-code').on 'click', (e)->
      e.stopPropagation()

  typeaheadCtrl.on 'typeahead:change', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')

  # Capture ENTER key press
  typeaheadCtrl.on 'keypress', (event, selection) ->
    if event.keyCode == 13
      event.preventDefault()

  # Retrieves the fachinfo, the URL should be of the form /fi/gtin/
  typeaheadCtrl.on 'typeahead:selected', (event, selection) ->
    if window.event.target.classList.contains('article-packinfo') || window.event.target.classList.contains('add-favourites-button')
      # Clicking on packinfo triggers prescription basket
      return
    if search_state == SearchState.Compendium || search_state == SearchState.Favourites
      if search_type == SearchType.FullText
        # FULL TEXT search
        fulltext_key = selection.keyword
        localStorage.setItem 'fulltext-search-id', selection.hash
        localStorage.setItem 'fulltext-search-key', fulltext_key
        $.ajax(jsRoutes.controllers.MainController.showFullTextSearchResult(language, selection.hash, fulltext_key))
        .done (response) ->
          window.location.assign '/' + language + '/fulltext?keyword=' + selection.keyword + '&key=' + typed_input
          console.log selection.hash + ' -> ' + fulltext_key + ' with language = ' + language
        .fail (jqHXR, textStatus) ->
          alert('ajax error')
      else
        # COMPENDIUM search
        localStorage.setItem 'compendium-selection-id', selection.id
        localStorage.setItem 'compendium-selection-ean', selection.eancode
        _search_type = getSearchTypeStr(search_type)
        $.ajax(jsRoutes.controllers.MainController.fachinfoRequest(language, selection.id, _search_type, typed_input))
        .done (response) ->
          window.location.assign '/' + language + '/fi?gtin=' + selection.eancode + '&type=' + _search_type + '&key=' + typed_input
          console.log selection.id + ' -> ' + selection.eancode + ' / search_type = ' + _search_type + '/ search_key = ' + typed_input
        .fail (jqHXR, textStatus) ->
          alert('ajax error')
    else if search_state == SearchState.Interactions
      # INTERACTIONS
      eancode = selection.eancode
      # add selection to basket
      if interactions_basket.length == 0
        interactions_basket = eancode
      else
        # add only if not already part of the list
        found = interactions_basket.search eancode
        # limit size of search result
        if eancode.length > 1260
          eancode = eancode.substring(0, 1260)
        if found < 0
          interactions_basket += ',' + eancode

      localStorage.setItem 'interactions-basket', interactions_basket
      # make ajax request to server -> basket
      $.ajax(jsRoutes.controllers.MainController.interactionsBasket(language, interactions_basket))
      .done (response) ->
        window.location.assign '/interactions/' + interactions_basket
        console.log 'added to basket: ' + selection.id + ' -> ' + selection.title + ' with language = ' + language
      .fail (jqHXR, textStatus) ->
        alert('ajax error')

  # Detect list related key up and key down events
  typeaheadCtrl.on 'typeahead:cursorchange', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    $('.twitter-typeahead').val(typed_input)

  # IMPORTANT: Claim focus!
  # This "hack" fixes the refresh issue for long lasting queries!
  $('.twitter-typeahead').focus().keyup()

  # Detect click on search field
  $('#search-field').on 'click', ->
    search_query = setSearchQuery(language, search_type)
    if search_state == SearchState.Compendium || search_state == SearchState.Favourites
      $('search-field').attr 'value', ''
      $('.twitter-typeahead').typeahead('val', '')
      # $('#fachinfo-id').replaceWith ''
      # $('#section-ids').replaceWith ''
    else if search_state == SearchState.Interactions
      $('search-field').attr 'value', ''
      $('.twitter-typeahead').typeahead('val', '')

  # Detect click on state buttons
  setSearchUIState = (state) ->
    search_state = state;
    localStorage.setItem 'search-state', state
    console.log "search state = " + search_state

  # Switch to "compendium" mode
  $('#compendium-button').on 'click', ->
    $(this).toggleClass 'nav-button-active'
    # set search state
    setSearchUIState(SearchState.Compendium)
    #
    selection_id = (String) localStorage.getItem 'compendium-selection-id'
    selection_ean = (String) localStorage.getItem 'compendium-selection-ean'

    $.ajax(jsRoutes.controllers.MainController.getFachinfo(language, selection_id))
    .done (response) ->
      window.location.assign '/' + language + '/fi/gtin/' + selection_ean
      console.log selection_id

  # Switch to "interactions" mode
  $('#interactions-button').on 'click', ->
    $(this).toggleClass 'nav-button-active'
    # disable full text search button
    disableButton SearchType.FullText
    # set search state
    setSearchUIState(SearchState.Interactions)
    # retrieve basket
    interactions_basket = (String) localStorage.getItem 'interactions-basket'
    if interactions_basket.length == 0
      interactions_basket = 'null'
    # make ajax request to server -> basket
    $.ajax(jsRoutes.controllers.MainController.interactionsBasket(language, interactions_basket))
    .done (response) ->
      window.location.assign '/interactions/' + interactions_basket
      console.log 'switching to interactions basket -> ' + interactions_basket
    .fail (jqHXR, textStatus) ->
      alert('ajax error')

  $('#prescriptions-button').on 'click', ->
    $(this).toggleClass 'nav-button-active'
    # disable full text search button
    disableButton SearchType.FullText
    # set search state
    setSearchUIState(SearchState.Prescriptions)
    if language == 'de'
      window.location.assign '/rezept'
    else
      window.location.assign '/prescription'

  $('#favourites-button').on 'click', ->
    $(this).toggleClass 'nav-button-active'
    # set search state
    setSearchUIState(SearchState.Favourites)
    window.location.assign '/favourites'

  # Detect click on search buttons
  setSearchType = (type) ->
    disableButton(search_type)
    search_type = type
    search_query = setSearchQuery(language, search_type)
    localStorage.setItem 'search-type', type
    console.log "search type = " + getSearchTypeStr(type) + " for " + typed_input
    # To force typeahead to reload the results, we have to make some changes to the query to trigger the event
    # However, when the input value is changed, it also updates typed_input, which means we lost the original value we want to reload to
    # Here we put the value in another variable so it doesn't get lost when typeahead('val', '') is called
    current_input = typed_input
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', current_input)

  $('#article-button').on 'click', ->
    setSearchType(SearchType.Title)

  $('#owner-button').on 'click', ->
    setSearchType(SearchType.Owner)

  $('#substance-button').on 'click', ->
    setSearchType(SearchType.Atc)

  $('#regnr-button').on 'click', ->
    setSearchType(SearchType.Regnr)

  $('#therapy-button').on 'click', ->
    setSearchType(SearchType.Therapie)

  $('#fulltext-button').on 'click', ->
    setSearchType(SearchType.FullText)

  window.triggerSearchATC = (atcCode)->
    setSearchType(SearchType.Atc)
    typeaheadCtrl.typeahead('val', atcCode)
    typeaheadCtrl.typeahead('open')

  $(document).on('click', '.add-favourites-button', (e)->
    hash = $(e.target).data('hash')
    if hash
      hashStr = String(hash)
      if Favourites.cachedFullText.has(hashStr)
        Favourites.removeFullTextHash(hashStr)
      else
        Favourites.addFullTextHash(hashStr)
    else
      regnrs = String($(e.target).data('regnrs'))
      if Favourites.cached.has(regnrs)
        Favourites.removeRegNrs(regnrs)
      else
        Favourites.addRegNrs(regnrs)
    $(e.target).toggleClass('--favourited')
  )
