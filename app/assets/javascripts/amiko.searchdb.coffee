$ ->
  typed_input = ''
  search_type = 1   # default search type is 'article'
  search_query = '/name?name='  # default search query

  articles = new Bloodhound(
    datumTokenizer: Bloodhound.tokenizers.obj.whitespace('name')
    queryTokenizer: Bloodhound.tokenizers.whitespace
    remote:
      wildcard: '%QUERY'
      url: search_query
      replace: (url, query) ->
        return search_query + query
  )

  # kicks off the loading/processing of "remote" and "prefetch"
  articles.initialize()

  $('#getArticle .twitter-typeahead').typeahead
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

  $('#getArticle .twitter-typeahead').on 'typeahead:asyncreceive', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')

  $('#getArticle .twitter-typeahead').on 'typeahead:change', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')

  # Retrieves the fachinfo, the URL should be of the form /fi/ean/
  $('#getArticle .twitter-typeahead').on 'typeahead:selected', (event, selection) ->
    $.ajax(jsRoutes.controllers.MainController.getFachinfo(selection.id))
    .done (response) ->
      console.log selection.id + ' -> ' + selection.title
      window.location.assign '/fi/ean/' + selection.eancode
    .fail (jqHXR, textStatus) ->
      alert('ajax error')

  # Detect list related key up and key down events
  $('#getArticle .twitter-typeahead').on 'typeahead:cursorchange', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    $('.twitter-typeahead').val(typed_input)

  # Detect click on search field
  $('#search-field').on 'click', ->
    $('search-field').attr 'value', ''
    $('#getArticle .twitter-typeahead').typeahead('val', '');
    if search_type == 1
      search_query = '/name?name='
    else if search_type == 2
      search_query = '/owner?owner='
    else if search_type == 3
      search_query = '/atc?atc='
    else if search_type == 4
      search_query = '/regnr?regnr='
    else if search_type == 5
      search_query = '/therapy?therapy='

  # Detect click events on filters
  $('#article_button').on 'click', ->
    search_type = 1
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Präparatname'

  $('#owner_button').on 'click', ->
    search_type = 2
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Inhaberin'

  $('#substance_button').on 'click', ->
    search_type = 3
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Wirkstoff/ATC'

  $('#regnr_button').on 'click', ->
    search_type = 4
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Zulassungsnummer'

  $('#therapy_button').on 'click', ->
    search_type = 5
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Therapie'
