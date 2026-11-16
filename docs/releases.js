(() => {
  const base = "https://github.com/nullnode1/notier-app/releases";
  const endpoint = "https://api.github.com/repos/nullnode1/notier-app/releases?per_page=100";
  const download = document.querySelector("[data-release-download]");
  const version = document.querySelector("[data-release-version]");
  const list = document.querySelector("[data-release-list]");
  const status = document.querySelector("[data-release-status]");
  if (!download && !list) return;

  const eligible = (release) => {
    if (release.draft || release.prerelease || typeof release.tag_name !== "string") return null;
    const tag = release.tag_name;
    const match = /^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?$/i.exec(tag);
    const url = base + "/tag/" + tag;
    if (!match || release.html_url !== url || !Array.isArray(release.assets)) return null;
    const assets = release.assets.filter((asset) => asset.state === "uploaded" &&
      Number(asset.size) > 0 && /\.apk$/i.test(asset.name) &&
      typeof asset.browser_download_url === "string" &&
      asset.browser_download_url.startsWith(base + "/download/" + tag + "/"));
    const components = match.slice(1).map((part) => Number(part ?? 0));
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
    const date = Number.isFinite(release.date.getTime())
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
      body.textContent = release.body || "GitHub’da bu sürüm için yazılı not yok.";
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

  const options = { headers: { Accept: "application/vnd.github+json" } };
  if (typeof AbortSignal.timeout === "function") options.signal = AbortSignal.timeout(7000);
  fetch(endpoint, options)
    .then((response) => {
      if (!response.ok) throw Error("Release request failed");
      return response.json();
    })
    .then((payload) => {
      if (!Array.isArray(payload)) throw Error("Invalid release response");
      const releases = payload.map(eligible).filter(Boolean).sort((left, right) => {
        const index = left.components.findIndex((part, i) => part !== right.components[i]);
        return index < 0 ? 0 : right.components[index] - left.components[index];
      });
      if (!releases.length) return;
      if (download) {
        download.href = releases[0].apk;
        download.querySelector("span").textContent = "APK’yı indir";
      }
      if (version) {
        version.textContent = releases[0].tag;
        version.hidden = false;
      }
      if (list) {
        list.replaceChildren();
        releases.slice(0, 20).forEach(addArticle);
        status.hidden = true;
        document.getElementById(location.hash.slice(1))?.scrollIntoView();
      }
    })
    .catch(() => {});
})();
