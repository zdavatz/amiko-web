/*
This file is part of AmiKoWeb.
Copyright (c) 2016 ML <cybrmx@gmail.com>
*/

function move_to_anchor(anchor) {
    /* document.getElementById(anchor).scrollIntoView(true); */
    var id = document.getElementById(anchor).getAttribute('id');
    $(document.body).scrollTop($('#'+id).offset().top-100);
    /*
    $('html, body').animate({
        scrollTop: $('#'+id).offset().top-120
    }, 1000);
    */
}

function download_links() {
    var options = {direction: 'right'};
    $('#download-links').toggle('slide', options, 250);
}
