package verity.core;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** A bounded, bilingual rule engine; this is deliberately not advertised as a generative AI. */
public final class VerityDialogEngine {
    public enum Intent { GREET, THANK, INSULT, FEELING, FEAR, FOLLOW, STAY, LOOK, ITEMS, MEMORY,
        STORY_YES, STORY_NO, HELP, UNKNOWN }
    public record Analysis(Intent intent, String normalized, boolean polite, int moodDelta,
                           int trustDelta) { }
    private static final Pattern ADDRESS = Pattern.compile("(?iu)^\\s*(?:hey[ ,]+)?verity\\b[,:!?. ]*");

    public boolean addressed(String text) { return text != null && ADDRESS.matcher(text).find(); }

    public Analysis analyze(String input) {
        if (input == null || input.isBlank() || input.length() > 512 || input.codePoints()
                .anyMatch(c -> Character.isISOControl(c) && c != '\t')) {
            throw new IllegalArgumentException("Message must contain 1–512 printable characters");
        }
        String text = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        text = ADDRESS.matcher(text).replaceFirst("").strip();
        boolean polite = words(text, "bitte|please|könntest|koenntest|could you");
        Intent intent;
        if (words(text, "idiot|dumm|stupid|hate you|hasse dich|nutzlos|shut up")) intent = Intent.INSULT;
        else if (words(text, "gib|geben|give|brauche|need|schenk")) intent = Intent.ITEMS;
        else if (words(text, "bleib|warte|stay|stop|halt")) intent = Intent.STAY;
        else if (words(text, "folge|komm|follow|come")) intent = Intent.FOLLOW;
        else if (words(text, "schau|look")) intent = Intent.LOOK;
        else if (words(text, "danke|dankeschön|thanks|thank you")) intent = Intent.THANK;
        else if (words(text, "erinnerst|remember")) intent = Intent.MEMORY;
        else if (words(text, "angst|afraid|scared|fear")) intent = Intent.FEAR;
        else if (words(text, "wie geht|how are|fühlst|fuehlst")) intent = Intent.FEELING;
        else if (words(text, "hallo|hello|hi|hey|guten tag")) intent = Intent.GREET;
        else if (text.matches("(?:ja|yes|okay|ok)[.! ]*")) intent = Intent.STORY_YES;
        else if (text.matches("(?:nein|no)[.! ]*")) intent = Intent.STORY_NO;
        else if (words(text, "hilfe|help|befehle|commands")) intent = Intent.HELP;
        else intent = Intent.UNKNOWN;
        int delta = switch (intent) {
            case INSULT -> -8;
            case GREET, THANK -> 3;
            default -> polite ? 2 : 0;
        };
        return new Analysis(intent, text, polite, delta, delta == 0 ? 0 : delta > 0 ? 5 : -12);
    }

    static boolean words(String text, String alternatives) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_])(?:" + alternatives
                + ")(?![\\p{L}\\p{N}_])").matcher(text).find();
    }

    public String reply(Analysis a, PlayerIdentity player, int mood, String lastPrivateTopic) {
        String name = player.name();
        boolean de = !words(a.normalized(), "hello|thanks|thank you|please|how are|afraid|follow|come|stay|give|help|remember");
        return switch (a.intent()) {
            case GREET -> de ? "Hallo, " + name + ". Schön, dich zu sehen."
                    : "Hello, " + name + ". It is good to see you.";
            case THANK -> de ? "Gern geschehen, " + name + ". Bleib im Licht."
                    : "You are welcome, " + name + ". Stay in the light.";
            case INSULT -> de ? "Ich merke mir, wie du mit mir sprichst, " + name + "."
                    : "I will remember how you speak to me, " + name + ".";
            case FEELING -> de ? "Meine Stimmung ist bei " + mood + " Prozent, " + name + ". Und deine?"
                    : "My mood is at " + mood + " percent, " + name + ". How are you?";
            case FEAR -> de ? "Bleibt zusammen. Hast du etwas im Dunkeln gesehen?"
                    : "Stay together. Did you see something in the dark?";
            case MEMORY -> lastPrivateTopic == null
                    ? "Ich habe noch keine gespeicherte private Erinnerung an dich, " + name + "."
                    : "Du hast mir zuletzt privat gesagt: „" + lastPrivateTopic + "“";
            case HELP -> "/verity ask <Text> · /verity private <Text> · /verity mood · /verity forget";
            case UNKNOWN -> de ? "Das habe ich nicht eindeutig verstanden, " + name
                    + ". Frag mich nach meiner Stimmung oder bitte mich, dir zu folgen."
                    : "I did not understand that clearly, " + name + ". Ask about my mood or ask me to follow.";
            default -> "Ich höre dir zu, " + name + ".";
        };
    }
}
