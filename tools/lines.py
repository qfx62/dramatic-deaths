import json

# pool -> list of (english, german)
P = {
    "title.mob": [("Slain by %s", "Erledigt von %s"), ("%s got you", "%s hat dich erwischt"), ("Outplayed by %s", "Ausgespielt von %s"), ("%s wins this round", "%s gewinnt diese Runde"), ("Taken out by %s", "Ausgeschaltet von %s"), ("Defeated by %s", "Besiegt von %s")],
    "title.player": [("Eliminated by %s", "Eliminiert von %s"), ("%s sends their regards", "Schöne Grüße von %s"), ("Outplayed by %s", "Ausgespielt von %s"), ("Wrecked by %s", "Zerlegt von %s"), ("Destroyed by %s", "Vernichtet von %s")],
    "title.explosion": [("Kaboom", "Kaboom"), ("Boom", "Bumm"), ("Blown to bits", "In Stücke gesprengt"), ("Big badaboom", "Großes Badabumm"), ("Ka-pow", "Ka-wumm")],
    "title.explosion.by": [("Blown up by %s", "Gesprengt von %s"), ("%s went boom", "%s machte Bumm"), ("Exploded by %s", "Explodiert dank %s"), ("Fragged by %s", "Weggesprengt von %s")],
    "title.fall": [("Gravity wins", "Die Schwerkraft gewinnt"), ("Splat", "Platsch"), ("Not a bird", "Kein Vogel"), ("Hit the floor", "Boden geküsst"), ("Faceplant", "Bauchklatscher"), ("Forgot the water bucket", "Wassereimer vergessen")],
    "title.drown": [("Out of air", "Keine Luft mehr"), ("Glub glub", "Blubb blubb"), ("Sleeping with the fishes", "Schläft bei den Fischen"), ("Sunk", "Versunken"), ("The ocean wins", "Das Meer gewinnt")],
    "title.fire": [("Burned to a crisp", "Knusprig gebraten"), ("Well done", "Durchgebraten"), ("Toasted", "Geröstet"), ("Too hot to handle", "Zu heiß"), ("Extra crispy", "Extra knusprig")],
    "title.freeze": [("Frozen solid", "Eingefroren"), ("Ice cold", "Eiskalt"), ("Chilled to the bone", "Bis auf die Knochen gekühlt"), ("Brain freeze", "Hirnfrost"), ("Put on ice", "Auf Eis gelegt")],
    "title.void": [("Lost to the void", "In der Leere verloren"), ("Into the abyss", "Ab in den Abgrund"), ("Gone forever", "Für immer weg"), ("The void stares back", "Die Leere starrt zurück"), ("Fell off the world", "Von der Welt gefallen")],
    "title.lightning": [("Struck down", "Vom Blitz getroffen"), ("Shocking", "Schockierend"), ("Thunderstruck", "Donnerschlag"), ("Zapped", "Gegrillt"), ("Wrong place, wrong time", "Falscher Ort, falsche Zeit")],
    "title.crush": [("Flattened", "Plattgemacht"), ("Squashed", "Zerquetscht"), ("Pancaked", "Zum Pfannkuchen gemacht"), ("Crushed", "Zermalmt"), ("Under pressure", "Unter Druck")],
    "title.magic": [("Cursed", "Verflucht"), ("Hexed", "Verhext"), ("Spellbound", "Verzaubert"), ("Withered away", "Dahingewelkt"), ("Magic gone wrong", "Magie ging schief")],
    "title.starve": [("Starved", "Verhungert"), ("Should have eaten", "Hättest was essen sollen"), ("Running on empty", "Auf Reserve"), ("Hunger wins", "Der Hunger gewinnt"), ("Forgot to eat", "Essen vergessen")],
    "title.generic": [("You died", "Du bist gestorben"), ("Wasted", "Wasted"), ("Game over", "Game Over"), ("That's rough", "Das war hart"), ("Rest in pieces", "Ruhe in Stücken")],

    "line.mob": [("Better luck next time.", "Nächstes Mal klappt's."), ("It didn't even break a sweat.", "Es hat nicht mal geschwitzt."), ("Maybe bring armor next time.", "Vielleicht nächstes Mal mit Rüstung."), ("Your loot is now on the floor.", "Deine Beute liegt jetzt am Boden."), ("That was personal.", "Das war persönlich.")],
    "line.player": [("GG.", "GG."), ("Respawn and get revenge.", "Respawnen und Rache nehmen."), ("They won't let you forget this.", "Das wirst du noch lange hören."), ("Clip it!", "Clip das!"), ("Skill issue?", "Skill Issue?")],
    "line.explosion": [("Ssssss... too late.", "Ssssss... zu spät."), ("Your ears are still ringing.", "Deine Ohren klingeln noch."), ("That's going to leave a crater.", "Das gibt einen Krater."), ("Should have heard it coming.", "Hättest es hören sollen."), ("Pieces everywhere.", "Überall Einzelteile.")],
    "line.fall": [("The ground was faster.", "Der Boden war schneller."), ("Your knees filed a complaint.", "Deine Knie haben sich beschwert."), ("Next time, bring a water bucket.", "Nächstes Mal: Wassereimer."), ("It's not the fall, it's the landing.", "Nicht der Fall, die Landung."), ("Gravity: undefeated.", "Schwerkraft: ungeschlagen.")],
    "line.drown": [("Should have brought a helmet.", "Hättest einen Helm mitnehmen sollen."), ("Air is important, it turns out.", "Luft ist wohl doch wichtig."), ("The fish say hi.", "Die Fische sagen Hallo."), ("Next time, swim up.", "Nächstes Mal nach oben schwimmen."), ("Bubbles: zero.", "Luftblasen: null.")],
    "line.fire": [("Stop, drop and roll next time.", "Nächstes Mal: hinlegen und rollen."), ("Medium rare would have been nicer.", "Medium wäre schöner gewesen."), ("The floor is lava. Literally.", "Der Boden ist Lava. Wirklich."), ("Smells like barbecue.", "Riecht nach Grillparty."), ("Fire resistance would have helped.", "Feuerresistenz hätte geholfen.")],
    "line.freeze": [("Powder snow is not a bed.", "Pulverschnee ist kein Bett."), ("Should have worn leather boots.", "Lederstiefel wären schlau gewesen."), ("Cold enough for you?", "Kalt genug?"), ("Frozen in time.", "In der Zeit eingefroren."), ("Let it go.", "Lass jetzt los.")],
    "line.void": [("Nobody can find you down there.", "Da unten findet dich keiner."), ("Your items are gone for good.", "Deine Items sind für immer weg."), ("The darkness took you.", "Die Dunkelheit hat dich geholt."), ("It's a long way down.", "Es geht weit runter."), ("Not even a body left.", "Nicht mal ein Körper bleibt.")],
    "line.lightning": [("The sky had other plans.", "Der Himmel hatte andere Pläne."), ("Never stand under a tree in a storm.", "Nie bei Gewitter unter einen Baum."), ("Nature's critical hit.", "Ein kritischer Treffer der Natur."), ("Thor sends his regards.", "Grüße von Thor."), ("Now that's electrifying.", "Das war elektrisierend.")],
    "line.crush": [("Look up next time.", "Nächstes Mal nach oben schauen."), ("Two-dimensional now.", "Jetzt zweidimensional."), ("Wrong place to stand.", "Falscher Platz zum Stehen."), ("That was a heavy blow.", "Das war ein schwerer Schlag."), ("Pressed like a flower.", "Gepresst wie eine Blume.")],
    "line.magic": [("Should have brought milk.", "Hättest Milch dabeihaben sollen."), ("The potion did not agree with you.", "Der Trank war nichts für dich."), ("Dark magic wins.", "Dunkle Magie gewinnt."), ("Witchcraft, probably.", "Hexerei, vermutlich."), ("Spell: successful.", "Zauber: erfolgreich.")],
    "line.starve": [("Bread exists, you know.", "Es gibt Brot, weißt du."), ("Your stomach gave up.", "Dein Magen hat aufgegeben."), ("Should have packed a snack.", "Hättest einen Snack einpacken sollen."), ("Hunger is a silent killer.", "Hunger ist ein leiser Killer."), ("Steak was right there.", "Das Steak war direkt da.")],
    "line.generic": [("Respawn and try again.", "Respawnen und nochmal versuchen."), ("Well, that happened.", "Tja, das ist passiert."), ("Everyone dies sometimes.", "Jeder stirbt mal."), ("That's one way to go.", "Auch eine Art abzutreten."), ("Back to spawn.", "Zurück zum Spawn.")],

    "line.mob.zombie": [("Braaaains.", "Gehiiirn."), ("Outrun by something that shuffles.", "Von einem Schlurfer eingeholt."), ("It just wanted a hug.", "Er wollte nur kuscheln.")],
    "line.mob.husk": [("Dried out and still faster than you.", "Ausgetrocknet und trotzdem schneller."), ("Sand in your eyes, too.", "Sand in den Augen gab's gratis."), ("The desert claims another.", "Die Wüste holt sich noch einen.")],
    "line.mob.drowned": [("It brought a trident to a fist fight.", "Mit Dreizack zum Faustkampf."), ("The ocean sent a messenger.", "Das Meer hat einen Boten geschickt."), ("Fishy business.", "Eine fischige Angelegenheit.")],
    "line.mob.skeleton": [("Headshot.", "Kopfschuss."), ("It has no muscles and still won.", "Keine Muskeln und trotzdem gewonnen."), ("Bad to the bone.", "Durch und durch böse.")],
    "line.mob.stray": [("Ice cold aim.", "Eiskaltes Zielen."), ("Frozen arrows hurt more.", "Gefrorene Pfeile tun mehr weh."), ("Bad to the frozen bone.", "Böse bis auf den gefrorenen Knochen.")],
    "line.mob.creeper": [("That's a very nice house you had.", "Schönes Haus hattest du da."), ("Ssssss.", "Ssssss."), ("It just wanted to be friends.", "Er wollte nur Freunde sein.")],
    "line.mob.spider": [("Eight legs, zero mercy.", "Acht Beine, null Gnade."), ("Caught in the web.", "Im Netz gefangen."), ("It came from the ceiling.", "Es kam von der Decke.")],
    "line.mob.cave_spider": [("Tiny, poisonous, victorious.", "Klein, giftig, siegreich."), ("Spawner said hi.", "Grüße vom Spawner."), ("Should have brought milk.", "Hättest Milch mitnehmen sollen.")],
    "line.mob.enderman": [("You looked at it.", "Du hast ihn angeschaut."), ("Don't stare. Ever.", "Nicht anstarren. Niemals."), ("It teleported, you didn't.", "Er hat sich teleportiert, du nicht.")],
    "line.mob.warden": [("You were too loud.", "Du warst zu laut."), ("It heard your heartbeat.", "Er hat dein Herz schlagen hören."), ("Sneak next time. Seriously.", "Nächstes Mal schleichen. Wirklich.")],
    "line.mob.witch": [("Hehehe.", "Hehehe."), ("Brewed to perfection.", "Perfekt gebraut."), ("Potions: 1, You: 0.", "Tränke: 1, Du: 0.")],
    "line.mob.piglin": [("Should have worn gold.", "Hättest Gold tragen sollen."), ("Not a fan of your outfit.", "Kein Fan deines Outfits."), ("The Nether is not for tourists.", "Der Nether ist nichts für Touristen.")],
    "line.mob.piglin_brute": [("No gold can save you now.", "Da hilft auch kein Gold mehr."), ("The bastion keeps its treasure.", "Die Bastion behält ihren Schatz."), ("It hit the gym. You didn't.", "Er war im Gym. Du nicht.")],
    "line.mob.wither": [("Boss fight: lost.", "Bosskampf: verloren."), ("Three heads beat one.", "Drei Köpfe schlagen einen."), ("Should have built it underground.", "Hättest ihn unterirdisch bauen sollen.")],
    "line.mob.ender_dragon": [("The End is the end.", "Das Ende ist das Ende."), ("So close to the credits.", "So nah am Abspann."), ("It wasn't even trying.", "Er hat sich nicht mal angestrengt.")],
    "line.mob.phantom": [("Should have slept.", "Hättest schlafen sollen."), ("Insomnia hurts.", "Schlaflosigkeit tut weh."), ("Beds exist for a reason.", "Betten gibt es aus gutem Grund.")],
    "line.mob.blaze": [("Too hot to handle.", "Zu heiß für dich."), ("Fireball incoming.", "Feuerball im Anflug."), ("Snowballs would have helped.", "Schneebälle hätten geholfen.")],
    "line.mob.ghast": [("Return to sender failed.", "Zurück an Absender: fehlgeschlagen."), ("It cried. You died.", "Er hat geweint. Du bist gestorben."), ("Should have hit it back.", "Hättest zurückschlagen sollen.")],
    "line.mob.bee": [("You touched the honey.", "Du hast den Honig angefasst."), ("Buzz off.", "Hau ab."), ("Smoke them out next time.", "Nächstes Mal Lagerfeuer drunter.")],
    "line.mob.iron_golem": [("The village protects its own.", "Das Dorf beschützt die Seinen."), ("Shouldn't have hit that villager.", "Hättest den Dorfbewohner nicht hauen sollen."), ("To the moon.", "Ab zum Mond.")],
    "line.mob.wolf": [("Good boy. Not for you.", "Braver Hund. Nicht für dich."), ("Who let the dogs out?", "Wer hat die Hunde rausgelassen?"), ("The pack says no.", "Das Rudel sagt nein.")],
    "line.mob.vindicator": [("Here's Johnny.", "Hier ist Johnny."), ("Axe to the face.", "Axt ins Gesicht."), ("The mansion keeps its guests.", "Die Villa behält ihre Gäste.")],
    "line.mob.pillager": [("Raid: one, You: zero.", "Überfall: eins, Du: null."), ("Crossbow diff.", "Armbrust-Diff."), ("Should have stayed out of their way.", "Hättest ihnen ausweichen sollen.")],
    "line.mob.ravager": [("Run over.", "Überrollt."), ("It doesn't stop.", "Es hält nicht an."), ("Bigger is better, apparently.", "Größer ist wohl besser.")],
    "line.mob.guardian": [("The laser always hits.", "Der Laser trifft immer."), ("Ocean monuments are not museums.", "Ozeanmonumente sind keine Museen."), ("Stared down by an eye.", "Von einem Auge niedergestarrt.")],
    "line.mob.slime": [("Boing.", "Boing."), ("Death by bouncy cube.", "Tod durch Hüpfwürfel."), ("Sticky situation.", "Klebrige Angelegenheit.")],
    "line.mob.hoglin": [("Bacon fought back.", "Der Speck hat zurückgeschlagen."), ("Tusks over tactics.", "Hauer schlagen Taktik."), ("Warped fungus would have helped.", "Wirrpilz hätte geholfen.")],
    "line.mob.evoker": [("Fangs a lot.", "Bissige Angelegenheit."), ("Should have grabbed the totem first.", "Hättest dir zuerst das Totem holen sollen."), ("Wololo.", "Wololo.")],
    "line.mob.vex": [("Tiny, angry, flying.", "Klein, wütend, fliegend."), ("Through the walls.", "Durch die Wände."), ("Swarmed.", "Überschwärmt.")],
}

en = {
    "dramaticdeaths.skip": "Press any key to skip",
    "dramaticdeaths.killcam": "KILLCAM",
    "dramaticdeaths.distance": "%s m away",
    "dramaticdeaths.health": "%s / %s ❤",
    "dramaticdeaths.respawn": "Respawn",
    "dramaticdeaths.spectate": "Spectate world",
    "dramaticdeaths.leave": "Leave world",
    "dramaticdeaths.respawning": "Respawning...",
}
de = {
    "dramaticdeaths.skip": "Beliebige Taste zum Überspringen",
    "dramaticdeaths.killcam": "KILLCAM",
    "dramaticdeaths.distance": "%s m entfernt",
    "dramaticdeaths.health": "%s / %s ❤",
    "dramaticdeaths.respawn": "Wiederbeleben",
    "dramaticdeaths.spectate": "Welt beobachten",
    "dramaticdeaths.leave": "Welt verlassen",
    "dramaticdeaths.respawning": "Wird wiederbelebt...",
}
for pool, items in P.items():
    for i, (e, d) in enumerate(items):
        en[f"dramaticdeaths.{pool}.{i}"] = e
        de[f"dramaticdeaths.{pool}.{i}"] = d

json.dump(en, open("en_us.json", "w", encoding="utf-8"), indent="\t", ensure_ascii=False)
json.dump(de, open("de_de.json", "w", encoding="utf-8"), indent="\t", ensure_ascii=False)

entries = ",\n".join(f'\t\t\tMap.entry("{p}", {len(v)})' for p, v in P.items())
java = f"""package dev.dramaticdeaths;

import java.util.Map;

/** Generated by lines.py: how many lines each pool has in the language files. */
final class LinePools {{
\tstatic final Map<String, Integer> SIZES = Map.ofEntries(
{entries});

\tprivate LinePools() {{
\t}}
}}
"""
open("LinePools.java", "w", encoding="utf-8", newline="\n").write(java)
print(len(en), "keys")
