$ ->
  ## export deleteRow function to GLOBAL space 'window'
  window.deleteRow = (tableID, currentRow) ->
    if localStorage.getItem 'language'
      language = (String) localStorage.getItem 'language'
    if localStorage.getItem 'interactions-basket'
      interactions_basket = (String) localStorage.getItem 'interactions-basket'

    if tableID == 'Delete_all'
      emptyInteractionsBasket language
    else if tableID == 'Interaktionen'
      # remove row
      table = document.getElementById tableID
      for row in table.rows
        if row == currentRow.parentNode.parentNode
          ean = row.cells[1].innerText
          # remove ean from interactions_basket
          interactions_basket = interactions_basket.replace(','+ean,'').replace(ean, '').replace(/^,|,$/g,'')
          localStorage.setItem 'interactions-basket', interactions_basket
          if interactions_basket
            $.ajax(jsRoutes.controllers.MainController.interactionsBasket(language, interactions_basket))
            .done (response) ->
              console.log 'interactions/' + interactions_basket
              window.location.assign '/interactions/' + interactions_basket
            .fail (jqHXR, textStatus) ->
              alert('ajax error')
          else
            emptyInteractionsBasket()

  emptyInteractionsBasket = (lang) ->
    interactions_basket = ''
    localStorage.setItem 'interactions-basket', interactions_basket
    $.ajax(jsRoutes.controllers.MainController.interactionsBasket(lang, 'null'))
    .done (response) ->
      console.log 'interactions/null'
      window.location.assign '/interactions/null'
    .fail (jqHXR, textStatus) ->
      alert('ajax error')

  window.callEPhaAPI = (ids) ->
    console.log ids
    $.post('/epha', { gtins: ids })
      .done (response)->
        window.open response
