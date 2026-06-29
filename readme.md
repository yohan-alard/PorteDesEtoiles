Projet : Ouverture automatique du portail via géolocalisation

Contexte

Portail électrique Samphi, piloté actuellement via la box domotique Somfy TaHoma.
Objectif : créer une application Android qui ouvre automatiquement le portail quand
le téléphone (et uniquement ce téléphone) arrive à proximité, sans dépendre du
badge de télépéage Ulysse (protocole propriétaire, non exploitable pour cet usage).

Principe retenu


App Android développée maison (le porteur du projet est développeur).
Géolocalisation du téléphone pour détecter l'approche du portail.
Déclenchement de l'ouverture via l'API locale TaHoma (mode développeur Somfy).
Authentification garantie par le fait que seule cette app, sur ce téléphone,
détient les identifiants/jetons d'accès à l'API — pas de détection "présence"
généraliste type Bluetooth/WiFi qui pourrait déclencher sur n'importe quel appareil.


Pourquoi pas le badge Ulysse


Protocoles de télépéage sécurisés et propriétaires.
Pas d'accès simple ni légal à l'identifiant du badge depuis l'extérieur.
Abandonné au profit de la géolocalisation smartphone.


API TaHoma


Somfy propose un mode développeur activable sur la TaHoma/TaHoma switch,
donnant accès à une API locale HTTP.
Documentation de référence : repo GitHub Somfy-Developer/Somfy-TaHoma-Developer-Mode.
Une interface Swagger est dispo pour tester les requêtes une fois le mode dev activé.
Étape à faire avant de coder : activer le mode développeur sur la box et récupérer
le token d'authentification local.


Gestion de la consommation batterie (point clé)

Stratégie à intervalle adaptatif :


Loin du portail : géolocalisation peu précise (réseau mobile/WiFi) et peu fréquente
(ex. vérification toutes les ~10 minutes).
En approche : bascule vers une localisation plus précise et plus fréquente
(GPS, vérification toutes les quelques secondes) uniquement une fois entré dans
un rayon de "pré-zone" autour du portail.
Objectif : éviter l'usage permanent du GPS haute précision, qui est le principal
poste de consommation batterie.
À définir lors du développement : taille des rayons (zone large peu précise /
zone proche haute précision), et seuils de bascule entre les deux modes.


Prochaines étapes


Activer le mode développeur TaHoma et tester l'API via Swagger.
Récupérer/stocker le token d'authentification de façon sécurisée dans l'app.
Développer la logique de géofencing à deux niveaux (grossier / précis).
Implémenter l'appel API d'ouverture du portail au franchissement de la zone proche.
Tester en conditions réelles le soir même.
