$ ->
  # Enum-equivalent, singleton
  SearchState =
    Compendium: 1
    Interactions: 2

  SearchType =
    Title: 1
    Owner: 2
    Atc: 3
    Regnr: 4
    Therapie: 5

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
    else if search_state == SearchState.Interactions
      $('#interactions-button').toggleClass 'nav-button-active'
  else
    search_state = 1  # default search state is 'compendium'
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
    return '/name?lang=' + lang + '&name='

  # default value
  search_query = setSearchQuery(language, SearchType.Title)

  start_time = new Date().getTime()

  typed_input = ''

  articles = new Bloodhound(
    datumTokenizer: Bloodhound.tokenizers.obj.whitespace('name')
    queryTokenizer: Bloodhound.tokenizers.whitespace
    remote:
      wildcard: '%QUERY'
      url: search_query
      replace: (url, query) ->
        return search_query + query
      filter: (list) ->
        document.getElementById('num-results').textContent=list.length
        return list
  )

  # kicks off the loading/processing of "remote" and "prefetch"
  articles.initialize()

  typeaheadCtrl = $('#input-form .twitter-typeahead')

  typeaheadCtrl.typeahead
    menu: $('#special-dropdown')
    hint: false
    highlight: false
    minLength: 1
  ,
    name: 'articles'
    displayKey: 'name'
    limit: '300'
    # "ttAdapter" wraps the suggestion engine in an adapter that is compatible with the typeahead jQuery plugin
    source: articles.ttAdapter()
    templates:
      suggestion: (data) ->
        if search_type == SearchType.Title
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='font-size:0.85em;'>#{data.packinfo}</span></div>"
        else if search_type == SearchType.Owner
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:#8888cc;font-size:1.0em;'><p>#{data.author}</p></span></div>"
        else if search_type == SearchType.Atc
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:gray;font-size:0.85em;'>#{data.atccode}</span></div>"
        else if search_type == SearchType.Regnr
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:#8888cc;font-size:1.0em;'><p>#{data.regnrs}</p></span></div>"
        else if search_type == SearchType.Therapie
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;font-size:1.0em;'><b>#{data.title}</b></p>\
          <span style='color:gray;font-size:0.85em;'>#{data.therapy}</span></div>"

  typeaheadCtrl.on 'typeahead:asyncrequest', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    start_time = new Date().getTime()

  typeaheadCtrl.on 'typeahead:asyncreceive', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    request_time = new Date().getTime() - start_time  # request time in [ms]

  typeaheadCtrl.on 'typeahead:change', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')

  # Retrieves the fachinfo, the URL should be of the form /fi/gtin/
  typeaheadCtrl.on 'typeahead:selected', (event, selection) ->
    console.log search_state
    if search_state == SearchState.Compendium
      localStorage.setItem 'compendium-selection-id', selection.id
      localStorage.setItem 'compendium-selection-ean', selection.eancode
      $.ajax(jsRoutes.controllers.MainController.getFachinfo(language, selection.id))
      .done (response) ->
        window.location.assign '/' + language + '/fi/gtin/' + selection.eancode
        console.log selection.id + ' -> ' + selection.title + ' with language = ' + language
      .fail (jqHXR, textStatus) ->
        alert('ajax error')
    else if search_state == SearchState.Interactions
      eancode = selection.eancode
      # add selection to basket
      if interactions_basket.length == 0
        interactions_basket = eancode
      else
        # add only if not already part of the list
        found = interactions_basket.search eancode
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

  # Detect click on search field
  $('#search-field').on 'click', ->
    search_query = setSearchQuery(language, search_type)
    console.log(search_query)
    if search_state == SearchState.Compendium
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

  # Detect click on search buttons
  setSearchType = (type) ->
    search_type = type
    localStorage.setItem 'search-type', type
    console.log "search type = " + type + " for " + typed_input
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)

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
