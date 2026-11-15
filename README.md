# Citizen Tab (citizentab)

Mod **serveur** NeoForge 21.1.215 / Minecraft 1.21.1 / Java 21.
La liste Tab n'affiche plus les pseudos : chaque joueur apparaît avec sa fiche RP

`Aldric Valmont • 32 ans • Forgeron`

Un joueur sans fiche apparaît en « Voyageur inconnu ». Rien à installer chez les joueurs.

## Joueurs
- `/fiche` : voir sa fiche
- `/fiche prenom <prénom>` · `/fiche nom <nom>` · `/fiche age <nombre>` · `/fiche profession <métier>`
- `/fiche valider` : verrouille la fiche (ensuite seul un admin peut la modifier)

À la connexion, un joueur dont la fiche n'est pas validée reçoit les commandes cliquables dans le chat.

## Admins (OP niveau 2)
- `/fiche admin liste`
- `/fiche admin voir <joueur>`
- `/fiche admin prenom|nom|profession <joueur> <texte>` · `/fiche admin age <joueur> <nombre>`
- `/fiche admin deverrouiller <joueur>` · `/fiche admin supprimer <joueur>`

Les fiches sont sauvegardées dans `world/data/citizentab.dat`.
Avec Citizen Noms : le Tab affiche la fiche RP, le nom de Citizen Noms reste dans le chat et au-dessus de la tête.
