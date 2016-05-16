$ ->
  typed_input = ''
  search_type = 1   # default search type is 'article'
  search_query = '/article?title=%QUERY'

  articles = new Bloodhound(
    datumTokenizer: Bloodhound.tokenizers.obj.whitespace('name')
    queryTokenizer: Bloodhound.tokenizers.whitespace
    remote:
      wildcard: '%QUERY'
      url: search_query
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
# "ttAdapter" wraps the suggestion engine in an adapter that
# is compatible with the typeahead jQuery plugin
    source: articles.ttAdapter()
    templates:
      suggestion: (data) ->
        if search_type == 1
          "<div style='display:table;vertical-align:middle;'><p style='color:#444444;'><b>#{data.title}</b></p><p style='color:#8888cc;'>#{data.author}</p></div>"
        else if search_type == 2
          "<div style='display:table;vertical-align:middle;'><p style='color:#444444;'><b>#{data.author}</b></p><p style='color:#8888cc;'>#{data.title}</p></div>"
        else if search_type == 3
          "<div style='display:table;vertical-align:middle;'><p style='color:#444444;'><b>#{data.substances}</b></p><p style='color:#8888cc;'>#{data.title}</p></div>"
        else if search_type == 4
          "<div style='display:table;vertical-align:middle;'><p style='color:#444444;'><b>#{data.atc_code}</b></p><p style='color:#8888cc;'>#{data.title}</p></div>"
        else if search_type == 5
          "<div style='display:table;vertical-align:middle;'><p style='color:#444444;'><b>#{data.atc_code}</b></p><p style='color:#8888cc;'>#{data.title}</p></div>"

  $('#getArticle .twitter-typeahead').on 'typeahead:asyncreceive', (event, selection) ->
    console.log 'ASYNC_RECV'
    typed_input = $('.twitter-typeahead').typeahead('val')

  $('#getArticle .twitter-typeahead').on 'typeahead:change', (event, selection) ->
    console.log 'CHANGE'
    typed_input = $('.twitter-typeahead').typeahead('val')

  # Retrieves the fachinfo, the URL should be of the form /fi/ean/
  $('#getArticle .twitter-typeahead').on 'typeahead:selected', (event, selection) ->
# $.post '/fi?id=' + selection.id
# $.get '/fi?id=' + selection.id
    $.ajax(jsRoutes.controllers.MainController.getFachinfo(selection.id))
    .done (response) ->
      console.log selection.id + ' -> ' + selection.name
      window.location.assign '/fi/id/' + selection.id
    .fail (jqHXR, textStatus) ->
      alert('error')

  # Detect list related key up and key down events
  $('#getArticle .twitter-typeahead').on 'typeahead:cursorchange', (event, selection) ->
    typed_input = $('.twitter-typeahead').typeahead('val')
    $('.twitter-typeahead').val(typed_input)

  # Detect click events on filters
  $('#article_button').on 'click', ->
    search_type = 1
    $('.twitter-typeahead').typeahead('val', '').typeahead('val', typed_input)
    $('#search-field').attr 'placeholder', 'Suche Präparat'

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

