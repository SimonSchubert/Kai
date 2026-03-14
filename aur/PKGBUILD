# Maintainer: Simon Schubert <sschubert89@gmail.com>
# https://github.com/SimonSchubert/Kai

pkgname=kai-bin
pkgver=1.9.6
pkgrel=1
pkgdesc='AI assistant powered by Ollama, Gemini, ChatGPT and more'
arch=('x86_64')
url='https://github.com/SimonSchubert/Kai'
license=('Apache-2.0')
depends=('hicolor-icon-theme')
provides=('kai')
conflicts=('kai')
options=('!strip')

source=("Kai-${pkgver}-linux-x86_64.tar.gz::https://github.com/SimonSchubert/Kai/releases/download/v${pkgver}/Kai-${pkgver}-linux-x86_64.tar.gz")
sha256sums=('046eee28b9823550c71e6c896d4e82a863e125f36d2cbce037247ba5d5715a2f')

package() {
    # Install application files
    install -dm755 "${pkgdir}/opt/kai"
    cp -r "${srcdir}/Kai/"* "${pkgdir}/opt/kai/"
    chmod -R go-w "${pkgdir}/opt/kai"

    # Install wrapper script
    install -Dm755 /dev/stdin "${pkgdir}/usr/bin/kai" << 'EOF'
#!/bin/sh
exec /opt/kai/bin/Kai "$@"
EOF

    # Install desktop entry
    install -Dm644 /dev/stdin "${pkgdir}/usr/share/applications/kai.desktop" << EOF
[Desktop Entry]
Name=Kai
Comment=AI assistant powered by Ollama, Gemini, ChatGPT and more
Exec=kai
Icon=kai
Type=Application
Categories=Utility;Network;
Keywords=AI;Chat;Ollama;Gemini;LLM;Assistant;
StartupWMClass=compose-window
Terminal=false
EOF

    # Install icon
    install -Dm644 /dev/stdin "${pkgdir}/usr/share/icons/hicolor/scalable/apps/kai.svg" << 'EOF'
<svg viewBox="0 0 90 90" xmlns="http://www.w3.org/2000/svg">
   <circle cx="35" cy="45" r="30" fill="#6200ee" />
   <circle cx="55" cy="45" r="30" fill="#8063C5" />
</svg>
EOF

    # Install license
    install -Dm644 "${srcdir}/Kai/lib/Kai.copyright" "${pkgdir}/usr/share/licenses/${pkgname}/LICENSE" 2>/dev/null ||
    install -Dm644 "${srcdir}/Kai/LICENSE" "${pkgdir}/usr/share/licenses/${pkgname}/LICENSE" 2>/dev/null || true
}

# Publishing steps:
# 1. Create AUR account at https://aur.archlinux.org
# 2. git clone ssh://aur@aur.archlinux.org/kai-bin.git
# 3. Copy PKGBUILD and .SRCINFO into the cloned repo
# 4. git add PKGBUILD .SRCINFO
# 5. git commit -m "Initial upload: kai-bin 1.7.9"
# 6. git push
