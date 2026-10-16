/*
 * La prise du son de la réunion — posée par Rapporteur dans la page de réunion
 * de son panneau (Meet, Teams, Zoom… ouverts DANS l'application).
 *
 * Pourquoi : la réunion est un son d'appel, qu'Android ne laisse capter par
 * aucune application. L'interprète ne l'entendait que par le micro, et au
 * haut-parleur sa voix y serait revenue : il restait en sous-titres. Pris ici,
 * à la source, le son des correspondants arrive à l'interprète sans passer par
 * le micro — sa voix peut sortir au haut-parleur (demande de Felly, 16/10/2026).
 *
 * Ce qu'elle prend : les flux REÇUS que la page joue (la voix des
 * correspondants), jamais le micro du client ni rien d'autre de la page. Elle
 * ne fait rien tant que l'interprète ne la demande pas (capter(true, niveau)),
 * et rend tout à l'arrêt : le son de la réunion revient à son niveau.
 */
(function () {
  "use strict";
  if (window.__rapporteurPrise) { return; }
  var pont = window.rapporteurReunion;
  if (!pont || typeof pont.son !== "function") { return; }

  var flux = [];       /* les flux reçus que la page a montrés */
  var elements = [];   /* les éléments audio ou vidéo qui les jouent */
  var branches = [];   /* { f, source } branchés au mélange */
  var ctx = null, melange = null;
  var actif = false;
  var niveau = 1;

  function estFlux(f) {
    try { return Boolean(f) && typeof f.getAudioTracks === "function" && f.getAudioTracks().length > 0; } catch (e) { return false; }
  }

  function noter(f, element) {
    if (!estFlux(f)) { return; }
    if (flux.indexOf(f) < 0) { flux.push(f); if (actif) { brancher(f); } }
    if (element && elements.indexOf(element) < 0) {
      elements.push(element);
      if (actif) { try { element.volume = niveau; } catch (e) { /* la page garde la main */ } }
    }
  }

  /* Une MediaStream confiée à un élément audio ou vidéo : c'est ainsi que Meet
     et Teams font entendre les correspondants. */
  var srcObject = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, "srcObject");
  if (srcObject && srcObject.set && srcObject.configurable) {
    Object.defineProperty(HTMLMediaElement.prototype, "srcObject", {
      configurable: true,
      enumerable: srcObject.enumerable,
      get: function () { return srcObject.get.call(this); },
      set: function (valeur) {
        srcObject.set.call(this, valeur);
        try { if (valeur && typeof MediaStream !== "undefined" && valeur instanceof MediaStream) { noter(valeur, this); } } catch (e) { /* rien */ }
      },
    });
  }

  /* Les pistes reçues par une liaison, même jouées autrement. */
  var Liaison = window.RTCPeerConnection;
  if (typeof Liaison === "function") {
    var Prise = function (a, b) {
      var pc = new Liaison(a, b);
      pc.addEventListener("track", function (e) {
        if (e.track && e.track.kind === "audio") { noter((e.streams && e.streams[0]) || new MediaStream([e.track]), null); }
      });
      return pc;
    };
    Prise.prototype = Liaison.prototype;
    try {
      Object.getOwnPropertyNames(Liaison).forEach(function (cle) {
        if (["length", "name", "prototype", "caller", "arguments"].indexOf(cle) < 0) { Prise[cle] = Liaison[cle]; }
      });
    } catch (e) { /* rien */ }
    window.RTCPeerConnection = Prise;
  }

  /* Les éléments déjà dans la page (réunion rejointe avant la prise). */
  function balayer() {
    var liste = document.querySelectorAll("audio, video");
    for (var i = 0; i < liste.length; i++) {
      var f = null;
      try { f = liste[i].srcObject; } catch (e) { f = null; }
      if (f && typeof MediaStream !== "undefined" && f instanceof MediaStream) { noter(f, liste[i]); }
    }
  }

  function brancher(f) {
    if (!ctx) { return; }
    for (var i = 0; i < branches.length; i++) { if (branches[i].f === f) { return; } }
    try {
      var source = ctx.createMediaStreamSource(f);
      source.connect(melange);
      branches.push({ f: f, source: source });
    } catch (e) { /* flux fini : rien à prendre */ }
  }

  /* Le mélange des correspondants, en morceaux de 16 bits vers l'application. */
  function ouvrir() {
    if (ctx) { if (ctx.state !== "running") { ctx.resume().catch(function () {}); } return; }
    ctx = new (window.AudioContext || window.webkitAudioContext)();
    melange = ctx.createGain();
    var traitement = ctx.createScriptProcessor(2048, 1, 1);
    var muet = ctx.createGain();
    muet.gain.value = 0;
    melange.connect(traitement);
    traitement.connect(muet);
    muet.connect(ctx.destination);
    traitement.onaudioprocess = function (e) {
      /* Rien tant qu'aucun flux reçu n'est branché : la salle comprend alors que la
         page ne donne pas son son, et l'interprète écoute par le micro. */
      if (!actif || !branches.length) { return; }
      var d = e.inputBuffer.getChannelData(0);
      var octets = new Uint8Array(d.length * 2);
      for (var i = 0; i < d.length; i++) {
        var v = d[i] > 1 ? 1 : (d[i] < -1 ? -1 : d[i]);
        var s = (v < 0 ? v * 32768 : v * 32767) | 0;
        octets[2 * i] = s & 255;
        octets[2 * i + 1] = (s >> 8) & 255;
      }
      var binaire = "";
      for (var j = 0; j < octets.length; j += 8192) { binaire += String.fromCharCode.apply(null, octets.subarray(j, j + 8192)); }
      try { pont.son(btoa(binaire), ctx.sampleRate); } catch (x) { /* l'application est partie */ }
    };
    ctx.resume().catch(function () {});
  }

  function poserNiveau() {
    elements.forEach(function (el) {
      try { var voulu = actif ? niveau : 1; if (el.volume !== voulu) { el.volume = voulu; } } catch (e) { /* rien */ }
    });
  }

  window.__rapporteurPrise = {
    /* oui : prendre le son des correspondants ; n : leur niveau dans la page
       pendant ce temps (la voix de l'interprète passe devant), 1 à l'arrêt. */
    capter: function (oui, n) {
      actif = Boolean(oui);
      niveau = typeof n === "number" && n >= 0 && n <= 1 ? n : 1;
      if (actif) { balayer(); ouvrir(); flux.forEach(brancher); }
      poserNiveau();
    },
    etat: function () {
      return JSON.stringify({ actif: actif, flux: flux.length, elements: elements.length, branches: branches.length, niveau: niveau });
    },
  };

  setInterval(function () {
    if (!actif) { return; }
    balayer();
    flux.forEach(brancher);
    poserNiveau();
  }, 1000);
})();
