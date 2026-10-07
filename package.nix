{
  lib,
  appimageTools,
  fetchurl,
  makeDesktopItem,
  writeText,
}:

let
  pname = "kai";
  version = "3.3.0";

  src = fetchurl {
    url = "https://github.com/SimonSchubert/Kai/releases/download/v${version}/Kai-${version}-linux.AppImage";
    hash = "sha256-8sNURmrBXTuWurGaEKIeHCEs1FVdgZVkq3TWhY4iQiM=";
  };

  icon = writeText "kai.svg" ''
    <svg viewBox="0 0 90 90" xmlns="http://www.w3.org/2000/svg">
      <circle cx="35" cy="45" r="30" fill="#6200ee" />
      <circle cx="55" cy="45" r="30" fill="#8063C5" />
    </svg>
  '';

  desktopItem = makeDesktopItem {
    name = pname;
    desktopName = "Kai";
    comment = "AI assistant with persistent memory";
    exec = pname;
    icon = pname;
    categories = [ "Utility" "Network" ];
    keywords = [ "AI" "Chat" "LLM" "Assistant" ];
    startupWMClass = "compose-window";
  };
in
appimageTools.wrapType2 {
  inherit pname version src;

  extraPkgs =
    pkgs: with pkgs; [
      libGL
      fontconfig
      freetype
      alsa-lib
      xorg.libX11
      xorg.libXext
      xorg.libXi
      xorg.libXrender
      xorg.libXtst
      xorg.libXrandr
      xorg.libXcursor
    ];

  extraInstallCommands = ''
    install -Dm644 ${desktopItem}/share/applications/${pname}.desktop \
      $out/share/applications/${pname}.desktop
    install -Dm644 ${icon} $out/share/icons/hicolor/scalable/apps/${pname}.svg
  '';

  meta = {
    description = "Open-source AI assistant with persistent memory";
    homepage = "https://github.com/SimonSchubert/Kai";
    license = lib.licenses.asl20;
    sourceProvenance = [ lib.sourceTypes.binaryNativeCode ];
    platforms = [ "x86_64-linux" ];
    mainProgram = pname;
  };
}
