(() => {
  const base = "https://github.com/nullnode1/notier-app/releases";
  const endpoint = "https://api.github.com/repos/nullnode1/notier-app/releases?per_page=30";
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
      body: typeof release.body === "string" ? release.body : "",
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
    const text = release.body.split(/\r?\n/)
      .map((line) => line.replace(/^\s*(?:#+\s*|[-*]\s+)/, "").trim())
      .find((line) => line.length >= 35 && !line.startsWith("![")) ?? "";
    article.append(header, heading);
    if (text) {
      const summary = document.createElement("p");
      summary.textContent = text.replace(/\*\*|`/g, "").slice(0, 340);
      article.append(summary);
    }
    const footer = document.createElement("footer");
    const notes = document.createElement("a");
    notes.href = release.url;
    notes.textContent = "GitHub’da yama notlarını oku";
    const apk = document.createElement("a");
    apk.href = release.apk;
    apk.textContent = "APK’yı indir";
    footer.append(notes, apk);
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
        releases.forEach(addArticle);
        status.hidden = true;
        document.getElementById(location.hash.slice(1))?.scrollIntoView();
      }
    })
    .catch(() => {});
})();
