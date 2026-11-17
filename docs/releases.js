(() => {
  const base = "https://github.com/nullnode1/notier-app/releases";
  const endpoint = "https://api.github.com/repos/nullnode1/notier-app/releases?per_page=100";
  const download = document.querySelector("[data-release-download]");
  const version = document.querySelector("[data-release-version]");
  const list = document.querySelector("[data-release-list]");
  const status = document.querySelector("[data-release-status]");
  const message = document.querySelector("[data-release-message]");
  if (!download && !list) return;

  const versionParts = (tag) => /^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?$/i.exec(tag)
    ?.slice(1).map((part) => Number(part ?? 0));
  const sortVersions = (left, right) => {
    const index = left.components.findIndex((part, i) => part !== right.components[i]);
    return index < 0 ? 0 : right.components[index] - left.components[index];
  };

  const eligible = (release) => {
    if (release.draft || release.prerelease || typeof release.tag_name !== "string") return null;
    const tag = release.tag_name;
    const components = versionParts(tag);
    const url = base + "/tag/" + tag;
    if (!components || release.html_url !== url || !Array.isArray(release.assets)) return null;
    const assets = release.assets.filter((asset) => asset.state === "uploaded" &&
      Number(asset.size) > 0 && typeof asset.name === "string" && /\.apk$/i.test(asset.name) &&
      typeof asset.browser_download_url === "string" &&
      asset.browser_download_url.startsWith(base + "/download/" + tag + "/"));
    const apk = assets.find((asset) => asset.name.toLowerCase() === "notier-v" + components.join(".") + ".apk") ??
      assets.find((asset) => asset.name.toLowerCase() === "notier.apk") ?? assets[0];
    return apk ? { tag, url, apk: apk.browser_download_url, components,
      title: typeof release.name === "string" && release.name.trim() ? release.name.trim() : tag,
      body: typeof release.body === "string" ? release.body.trim().slice(0, 20000) : "",
      date: new Date(release.published_at) } : null;
  };

  function addArticle(release) {
    const article = document.createElement("article");
    article.className = "release-article";
    article.id = release.tag.replace(/\./g, "-");
    const header = document.createElement("p");
    header.className = "platform";
    const date = release.date && Number.isFinite(release.date.getTime())
      ? new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium" }).format(release.date)
      : "";
    header.textContent = [release.tag, date].filter(Boolean).join(" · ");
    const heading = document.createElement("h2");
    heading.textContent = release.title;
    const body = document.createElement("div");
    body.className = "release-markdown";
    if (release.body && window.marked && window.DOMPurify) {
      const html = marked.parse(release.body, { gfm: true });
      const fragment = DOMPurify.sanitize(html, {
        RETURN_DOM_FRAGMENT: true,
        ALLOWED_TAGS: ["p", "a", "br", "hr", "strong", "b", "em", "i", "del", "s",
          "blockquote", "pre", "code", "ul", "ol", "li", "input",
          "h1", "h2", "h3", "h4", "h5", "h6", "table", "thead", "tbody", "tr", "th", "td"],
        ALLOWED_ATTR: ["href", "title", "start", "type", "checked", "disabled", "align"],
        ALLOW_DATA_ATTR: false,
        ALLOW_ARIA_ATTR: false,
        FORBID_ATTR: ["src", "style"]
      });
      for (const link of fragment.querySelectorAll("a[href]")) {
        try {
          const url = new URL(link.getAttribute("href"), release.url);
          if (url.protocol === "http:" || url.protocol === "https:") link.href = url.href;
          else link.removeAttribute("href");
        } catch {
          link.removeAttribute("href");
        }
      }
      for (const checkbox of fragment.querySelectorAll("input")) {
        if (checkbox.type === "checkbox") checkbox.disabled = true;
        else checkbox.remove();
      }
      for (const table of fragment.querySelectorAll("table")) {
        const scroll = document.createElement("div");
        scroll.className = "markdown-table";
        scroll.tabIndex = 0;
        table.replaceWith(scroll);
        scroll.append(table);
      }
      body.append(fragment);
    } else {
      body.textContent = release.body || "Bu sürüm için yazılı not yok.";
    }

    const footer = document.createElement("footer");
    footer.className = "release-actions";
    const notes = document.createElement("a");
    notes.className = "button secondary";
    notes.href = release.url;
    notes.textContent = "GitHub’da oku";
    const apk = document.createElement("a");
    apk.className = "button primary";
    apk.href = release.apk;
    apk.textContent = "APK’yı indir";
    footer.append(notes, apk);
    article.append(header, heading, body);
    article.append(footer);
    list.append(article);
  }

  const storageKey = "notier.github.releases.v1";
  const interval = 12 * 60 * 60 * 1000;
  const retryInterval = 15 * 60 * 1000;
  const now = Date.now();
  const recent = (at, age) => Number.isFinite(at) && now >= at && now - at < age;
  let cache = null;
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey));
    if (saved && Array.isArray(saved.payload)) cache = saved;
  } catch {}
  const save = (value) => {
    try { localStorage.setItem(storageKey, JSON.stringify(value)); } catch {}
  };
  const catalog = (payload) => payload.filter((entry) => entry && typeof entry === "object")
    .map(eligible).filter(Boolean).sort(sortVersions).slice(0, 20);

  function render(payload) {
    const releases = catalog(payload);
    const latest = releases[0];
    if (download && latest) {
      download.href = latest.apk;
      download.querySelector("span").textContent = "APK’yı indir";
    }
    if (version && latest) {
      version.textContent = latest.tag;
      version.hidden = false;
    }
    if (list) {
      list.replaceChildren();
      releases.forEach(addArticle);
      if (status) status.hidden = releases.length > 0;
      if (message && !releases.length) message.textContent = "Listelenecek Android sürümü bulunamadı.";
      document.getElementById(location.hash.slice(1))?.scrollIntoView();
    }
    return releases.length;
  }

  const savedCount = cache ? render(cache.payload) : 0;
  if (cache && recent(cache.checkedAt, interval)) return;
  if (cache && recent(cache.attemptedAt, retryInterval)) {
    if (!savedCount && message) message.textContent = "GitHub sürümleri şu anda alınamıyor.";
    return;
  }
  if (navigator.onLine === false) {
    if (!savedCount && message) message.textContent = "Çevrimdışısın. Yama notları için internete bağlan.";
    return;
  }
  if (!savedCount && message) message.textContent = "GitHub sürümleri yükleniyor…";
  const headers = { Accept: "application/vnd.github+json" };
  if (typeof cache?.etag === "string") headers["If-None-Match"] = cache.etag;
  const options = { headers };
  if (typeof AbortSignal.timeout === "function") options.signal = AbortSignal.timeout(7000);
  fetch(endpoint, options)
    .then(async (response) => {
      if (response.status === 304 && cache) {
        save({ ...cache, checkedAt: now, attemptedAt: now });
        return;
      }
      if (!response.ok) throw Error("Release request failed");
      const payload = await response.json();
      if (!Array.isArray(payload)) throw Error("Invalid release response");
      const records = payload.filter((entry) => entry && typeof entry === "object").slice(0, 100).map((entry) => ({
        tag_name: entry.tag_name,
        html_url: entry.html_url,
        draft: entry.draft,
        prerelease: entry.prerelease,
        name: entry.name,
        body: typeof entry.body === "string" ? entry.body.slice(0, 20000) : "",
        published_at: entry.published_at,
        assets: Array.isArray(entry.assets) ? entry.assets.filter(Boolean).map((asset) => ({
          state: asset.state, size: asset.size, name: asset.name, browser_download_url: asset.browser_download_url
        })) : []
      }));
      save({ payload: records, checkedAt: now, attemptedAt: now, etag: response.headers.get("etag") });
      render(records);
    })
    .catch(() => {
      save({ ...(cache || { payload: [] }), attemptedAt: now });
      if (!savedCount && message) message.textContent = "GitHub sürümleri şu anda alınamıyor.";
    });
})();
