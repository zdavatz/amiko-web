$ ->
  if localStorage.getItem 'search-type'
    search_type = (Number) localStorage.getItem 'search-type'
  else
    search_type = 1   # default search type is 'article'

  setSearchQuery = (type) ->
    if type == 1
      return '/name?name='
    else if type == 2
      return '/owner?owner='
    else if type == 3
      return '/atc?atc='
    else if type == 4
      return '/regnr?regnr='
    else if type == 5
      return '/therapy?therapy='
    return '/name?name='

  # default value
  search_query = setSearchQuery(1)

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
        console.log 'num results = ' + list.length
        return list
  )

  # kicks off the loading/processing of "remote" and "prefetch"
  articles.initialize()

  typeaheadCtrl = $('#getArticle .twitter-typeahead')

  typeaheadCtrl.typeahead
    menu: $('#special-dropdown')
    hint: false
    highlight: false
    minLength: 1
  ,
    name: 'articles'
    displayKey: 'name'
    limit: '40'
    # "ttAdapter" wraps the suggestion engine in an adapter that is compatible with the typeahead jQuery plugin
    source: articles.ttAdapter()
    templates:
      suggestion: (data) ->
        if search_type == 1
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;'><b>#{data.title}</b></p>\
          <p style='color:#cccccc;'>#{data.packinfo}</p></div>"
        else if search_type == 2
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;'><b>#{data.title}</b></p>\
          <p style='color:#8888cc;'>#{data.author}</p></div>"
        else if search_type == 3
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;'><b>#{data.title}</b></p>\
          <p style='color:#8888cc;'>#{data.atccode}</p></div>"
        else if search_type == 4
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;'><b>#{data.title}</b></p>\
          <p style='color:#8888cc;'>#{data.regnrs}</p></div>"
        else if search_type == 5
          "<div style='display:table;vertical-align:middle;'>\
          <p style='color:#444444;'><b>#{data.title}</b></p>\
          <p style='color:#8888cc;'>#{data.therapy}</p></div>"

  typeaheadCtrl.on 'typeahead:asyncrequest', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    # console.log 'request = ' + typed_input
    start_time = new Date().getTime()

  typeaheadCtrl.on 'typeahead:asyncreceive', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    # console.log 'receive = ' + typed_input
    request_time = new Date().getTime() - start_time  # request time in [ms]

  typeaheadCtrl.on 'typeahead:change', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')

  # Retrieves the fachinfo, the URL should be of the form /fi/gtin/
  typeaheadCtrl.on 'typeahead:selected', (event, selection) ->
    $.ajax(jsRoutes.controllers.MainController.getFachinfo(selection.id))
    .done (response) ->
      window.location.assign '/fi/gtin/' + selection.eancode
      console.log selection.id + ' -> ' + selection.title
    .fail (jqHXR, textStatus) ->
      alert('ajax error')

  # Detect list related key up and key down events
  typeaheadCtrl.on 'typeahead:cursorchange', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    $('.twitter-typeahead').val(typed_input)

  # Detect click on search field
  $('#search-field').on 'click', ->
    $('search-field').attr 'value', ''
    $('.twitter-typeahead').typeahead('val', '');
    search_query = setSearchQuery(search_type)

  disableAllButtons = ->
    localStorage.setItem 'search-type', search_type
    $('#article-button').removeClass('active')
    $('#owner-button').removeClass('active')
    $('#substance-button').removeClass('active')
    $('#regnr-button').removeClass('active')
    $('#therapy-button').removeClass('active')

  # Detect click events on filters
  $('#article-button').on 'click', ->
    search_type = 1
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Präparatname...'
    disableAllButtons()
    $(this).toggleClass('active')

  $('#owner-button').on 'click', ->
    search_type = 2
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Inhaberin...'
    disableAllButtons()
    $(this).toggleClass('active')

  $('#substance-button').on 'click', ->
    search_type = 3
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Wirkstoff/ATC...'
    disableAllButtons()
    $(this).toggleClass('active')

  $('#regnr-button').on 'click', ->
    search_type = 4
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Zulassungsnummer...'
    disableAllButtons()
    $(this).toggleClass('active')

  $('#therapy-button').on 'click', ->
    search_type = 5
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Therapie...'
    disableAllButtons()
    $(this).toggleClass('active')
