/*
This file is part of AmiKoWeb.
Copyright (c) 2016 ML <cybrmx@gmail.com>
*/

/**
 * Moves site to given anchor
 * @param anchor
 */
function move_to_anchor(anchor) {
    /* document.getElementById(anchor).scrollIntoView(true); */
    var id = document.getElementById(anchor).getAttribute('id');
    $(window).scrollTop($('#'+id).offset().top-120);
    /*
    $('html, body').animate({
        scrollTop: $('#'+id).offset().top-120
    }, 1000);
    */
}
/**
 * Slide in of the download links on the right side
 */
function download_links() {
    var options = {direction: 'right'};
    $('#download-links').toggle('slide', options, 250);
}

/**
 * Hide Header on scroll down
 */
var didScroll;
var lastScrollTop = 0;
var delta = 5;

$(window).scroll(function(event) {
    didScroll = true;
});

setInterval(function() {
    if (didScroll) {
        hasScrolled();
        didScroll = false;
    }
}, 250);

function hasScrolled() {
    var st = $(this).scrollTop();
    var headerHeight = $('header').outerHeight();

    // Make sure they scroll more than delta
    if (Math.abs(lastScrollTop - st) <= delta)
        return;

    // If they scrolled down and are past the navbar, add class .nav-up.
    if (st > lastScrollTop && st > headerHeight){
        // Scroll Down
        $('header').removeClass('header-down').addClass('header-up');
        $('#flex-aside-two').removeClass('section-ids-down').addClass('section-ids-up');
    } else {
        // Scroll Up
        if (st < 80) {
            $('header').removeClass('header-up').addClass('header-down');
            $('#flex-aside-two').removeClass('section-ids-up').addClass('header-down');
        }
    }

    lastScrollTop = st;
}