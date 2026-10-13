(function(){
  var W = 1100, want = 'width=' + W;
  function fix(){
    var m = document.querySelector('meta[name="viewport"]');
    if (m) { if (m.getAttribute('content') !== want) m.setAttribute('content', want); }
    else if (document.head) { m = document.createElement('meta'); m.name = 'viewport'; m.content = want; document.head.appendChild(m); }
  }
  fix();
  if (!window.__toggVp) {
    window.__toggVp = true;
    var mo = new MutationObserver(fix);
    mo.observe(document, { childList: true, subtree: true });
    setTimeout(function(){ mo.disconnect(); }, 15000);
  }
})();
