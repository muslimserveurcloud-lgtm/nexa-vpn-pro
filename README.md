# MUSLIM VPN PRO (Android)

Version 0.4 : navigation façon HTTP Custom (tiroir + onglets Accueil / Profils / Réglages / Guide),
utilitaires (IP Finder, Auto Ping, Speedtest, Short URL, Hardware ID, Response checker, Share Net),
journal et débits en direct, MTU et applications exclues du VPN.
Version 0.3 : VMess, VLESS et Trojan (noyau Xray) en plus du SSH.
Version 0.2 : activation par token, Hardware ID, compteur de consommation et
**moteur SSH** (SSH direct, proxy HTTP, payload, SNI/TLS) avec envoi de la consommation au serveur.

## Config d'un serveur SSH (panneau admin > Serveurs)
- Type : `ssh`, hôte et port du serveur SSH.
- Configuration (JSON) :
  `{"payload":"GET / HTTP/1.1[crlf]Host: exemple.com[crlf]Upgrade: websocket[crlf][crlf]","sni":"","proxy":"","tls_insecure":false}`
  - `payload` : balises `[crlf] [cr] [lf] [host] [port] [host_port] [protocol] [ua] [split]`
  - `sni` : active TLS avec ce nom (SSH dans TLS)
  - `proxy` : `hote:port` d'un proxy HTTP par lequel passer
  - `tls_insecure` : `true` pour accepter un certificat non valide
- Identifiants secrets (JSON) : `{"username":"...","password":"..."}`

## Construire l'APK (GitHub Actions)
1. Envoie tout ce dossier (y compris `.github`) dans un dépôt GitHub.
2. Actions > Build APK > Run workflow.
3. Télécharge l'artefact `MuslimVpnPro-debug-apk` et installe `app-debug.apk`.

Le workflow compile aussi tun2socks (hev-socks5-tunnel) avec le NDK. Si une étape échoue,
envoie le journal de l'étape en erreur pour correction.

## APK signé (mises à jour sans désinstaller)
La clé de signature n'est jamais dans le dépôt : elle est dans les secrets GitHub
(KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). Tant que tu gardes la même clé,
chaque nouvel APK s'installe par-dessus l'ancien. Le numéro de version interne augmente tout seul
à chaque compilation. Artefact : `MuslimVpnPro-signed-apk`.

## Fichiers de configuration .mvpn
Le panneau génère un fichier `.mvpn` (token + nom + appareil autorisé). Sur le téléphone du client :
Partager / Ouvrir avec > MUSLIM VPN PRO. L'application importe la configuration et l'active.
L'identifiant de l'appareil (`MVPN-XXXX-XXXX-XXXX`) s'affiche dès la page d'activation ; un token lié à un
identifiant n'est accepté que sur cet appareil (contrôle côté serveur).
