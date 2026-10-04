# Yacht Dice

Two to four players share five small dice and fill twelve categories each. Every turn permits up to three rolls. Dice settle into the same five positions in a row; clicking moves a die into its keep slot, and clicking again returns it. Kept dice retain their value during rerolls. Adjusting slots after the third roll does not grant another roll.

Click **Roll Dice** in front of the tray. The twelve rows on the left show everyone's scores and the current player's available score. A private cursor frame outlines that player's aimed score cell or the aimed die. Click an unused category to finish the turn; choosing an unmet category writes zero. Sneak and right-click either table to open the room menu. Actions stay locked during the throw animation, and room identity, turn and revision checks apply to both world and menu actions.

## Scoring

| Category | Score |
| --- | --- |
| Ones through Sixes | Sum of matching dice |
| Choice | Sum of all five dice |
| Four of a Kind | Sum of all dice with at least four matching |
| Full House | Sum of all dice with exactly three plus two matching |
| Small Straight | 15 for a run of at least four distinct values |
| Large Straight | 30 for five consecutive values |
| Yacht | 50 for five matching dice |

An upper-section subtotal of at least 63 adds 35 points. There is no extra Yacht bonus. Highest total after twelve categories wins; equal highest totals draw. This preserves the plugin's seeded 12-category variant, matching the supplied score-sheet layout. The reference game supports two players; the plugin retains its existing two-to-four-player rules.

The provided video could not be retrieved during development. The user screenshots and [Clubhouse Games Yacht Dice guide](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/yacht-dice) were used to check the operation and category values.

## Rendering and upgrade

Vanilla uses reusable six-face dice, a raised dice tray and a flat score table without raised rails. CraftEngine adds `yacht_table` and `yacht_die`; both modes share cursor geometry, and mixed mode creates only the nearby players' required layers. The packed die has the same top and opposing-face orientations as the native die. The pack must match the JAR's bundled hash.

Existing seeded Yacht histories remain readable. The offline upgrade tool maps `score.category.0` through `.11` to semantic names and preserves custom values. Back up and prepare the language directory before installing the new JAR; the test-server deployment performs this conversion before restart.
