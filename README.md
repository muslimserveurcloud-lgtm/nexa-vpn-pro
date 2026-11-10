# NEXA VPN PRO — Android

Application native Android initiale pour NEXA VPN PRO.

## Compilation automatique

Chaque push sur `main` déclenche GitHub Actions et compile l'APK de débogage.
Après un build réussi, téléchargez `app-debug.apk` depuis l'onglet **Actions → dernière exécution → Artifacts**.

## État actuel

Cette première version met en place l'architecture et les écrans de base :
- Authentification du token par l'API publique configurée
- Hardware ID local `NEXA-...`
- Écran d'accueil sombre en français avec 5 onglets
- Service Android `VpnService` et demandes de permission système
- Débogage de base et journal de crash
- Moteur SSH initial et interfaces pour UDP, DNST et Vitora

## Important

Les moteurs UDP, DNST, VMess, VLESS, Trojan et Vitora nécessitent leurs implémentations natives et tests d'intégration au serveur correspondant. Le TUN est créé, mais cette version ne relaie pas encore tout le trafic Internet via les moteurs. Ne la présentez pas comme un VPN prêt pour production. Les routes API `/api/public/verify-token` et `/api/public/report-usage` doivent aussi être disponibles sur le panel avec authentification et validation appropriées.

Ne mettez aucun mot de passe de serveur ou secret dans le code ou dans GitHub. Utilisez les secrets de dépôt pour les clés de signature de version ultérieures.
