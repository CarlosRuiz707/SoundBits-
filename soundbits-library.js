/* Organiza el inicio y las carpetas locales tras cargar la app.
   YouTube Music conserva su portada propia; SoundBits muestra el home sin tracks locales. */
(function () {
  "use strict";
  if (!window.render || !window.lib || !window.lists || !window.likes) return;

  function syncManagedFolder(key, name, tracks) {
    var folder = null;
    for (var i = 0; i < lists.length; i++) {
      if (lists[i] && lists[i]._soundbitsFolder === key) { folder = lists[i]; break; }
    }
    if (!folder) {
      for (var j = 0; j < lists.length; j++) {
        if (lists[j] && lists[j].name === name) { folder = lists[j]; break; }
      }
    }
    var items = tracks.map(function (track) {
      return { t: track.title || track.t || "Sin título", a: track.artist || track.a || "" };
    });
    var changed = false;
    if (!folder) {
      folder = { name: name, items: [], _soundbitsFolder: key };
      lists.push(folder);
      changed = true;
    }
    if (folder.name !== name) { folder.name = name; changed = true; }
    if (folder._soundbitsFolder !== key) { folder._soundbitsFolder = key; changed = true; }
    var previous = Array.isArray(folder.items) ? folder.items : [];
    if (JSON.stringify(previous) !== JSON.stringify(items)) { folder.items = items; changed = true; }
    return changed;
  }

  function syncSoundBitsFolders() {
    if (!Array.isArray(window.lib) || !Array.isArray(window.lists)) return;
    var localTracks = lib.filter(function (track) {
      return track && (track.mid || (track.src && /^file:|^blob:/.test(track.src)));
    });
    var likedTracks = localTracks.filter(function (track) { return !!likes[track.k]; });
    var changed = syncManagedFolder("local", "Canciones locales", localTracks);
    changed = syncManagedFolder("likes", "Me gusta", likedTracks) || changed;
    if (changed && typeof window.sv === "function") window.sv();
  }

  var originalRender = window.render;
  window.render = function () {
    syncSoundBitsFolders();
    var result = originalRender.apply(this, arguments);
    if (window.tab === "home" && !window.open) {
      var main = document.getElementById("main");
      if (main) {
        main.innerHTML = '<section class="sb-home">' +
          '<div class="ysh"><div><small>SOUNDBITS</small><b>Inicio</b></div></div>' +
          '<div class="card sb-home-card"><b>Tu música, organizada</b>' +
          '<div class="a">Las canciones locales están en su propia carpeta. Tus favoritos se guardan en Me gusta.</div></div>' +
          '<h3 class="hs">Tu colección</h3>' +
          '<div class="grid hq">' +
          '<button class="tile" data-sb-route="likes"><div class="cv" style="background:#5b1830">♥</div><b>Me gusta</b></button>' +
          '<button class="tile" data-sb-route="local"><div class="cv" style="background:#263650">♫</div><b>Canciones locales</b></button>' +
          '</div>' +
          '<h3 class="hs">Descubre</h3>' +
          '<div class="grid hq">' +
          '<button class="tile" data-sb-route="search"><div class="cv" style="background:#303030">⌕</div><b>Explorar música</b></button>' +
          '<button class="tile" data-sb-route="podcast"><div class="cv" style="background:#303030">◖</div><b>Podcasts</b></button>' +
          '</div></section>';
      }
      document.querySelectorAll("#nav button").forEach(function (button) {
        button.classList.toggle("on", button.dataset.t === "home");
      });
    }
    return result;
  };

  document.addEventListener("click", function (event) {
    var button = event.target.closest("[data-sb-route]");
    if (!button) return;
    var route = button.dataset.sbRoute;
    if (route === "likes" || route === "local") {
      window.tab = "lib";
      window.open = null;
      window.libChip = route === "likes" ? "Me gusta" : "Canciones";
      window.render();
      window.scrollTo(0, 0);
    } else if (route === "podcast" || route === "search") {
      window.tab = route;
      window.open = null;
      window.render();
      window.scrollTo(0, 0);
      if (route === "search") setTimeout(function () {
        var input = document.getElementById("q");
        if (input) input.focus();
      }, 60);
    }
  });

  /* Resync after Android's asynchronous MediaStore scan adds local tracks. */
  syncSoundBitsFolders();
  window.render();
})();
