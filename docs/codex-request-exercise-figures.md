# Request: illustrated, animated exercise and yoga figures

## Summary

This repo has two standalone web pages that show a small animated figure for every exercise or yoga pose:

| Page | File | Items |
|---|---|---|
| Circuit Builder (interval workout planner and timer) | `workout-planner/index.html` | 46 exercises |
| Yoga Flow Builder (yoga sequence builder and timer) | `yoga-planner/index.html` | 32 poses |

The current figures are procedurally drawn from a 2D joint-angle skeleton: capsule limbs, a circle head and a polygon torso. They look crude and stiff, and several poses are anatomically wrong or unreadable. **Please replace the figure artwork and animation in both pages with good-looking, accurate, consistent illustrated characters.** The rest of each page (layout, generator, timer logic, copy) should stay as it is.

## What is wrong with the current figures

- They look like a rig, not a person. The limbs are uniform tubes, the joints have no shape, and nothing overlaps naturally.
- Proportions are off. The head is too big and the torso is a flat slab.
- Only a single flat side or front view is available, so many poses lose the information that matters:
  - Side Plank looks the same as Forearm Plank.
  - Supine Twist, Butterfly, Pigeon, Lateral Lunge and Skater are hard to read.
  - Russian Twist doesn't show any rotation.
- Some poses are simply wrong or unclear:
  - Child's Pose reads as a huddle.
  - Easy Seat is tiny.
  - The legs in Russian Twist look odd.
  - The bend in Happy Baby and Knees to Chest is unclear.
- Motion is linear joint-angle interpolation. It's stiff, with no weight shift, follow-through or easing per body part.

## What good looks like

- **One consistent character per page.** It should be friendly and simple (flat vector illustration, not realistic 3D), the same person in every exercise, with natural proportions. Feel free to make the yoga character and the circuit character the same person in different outfits.
- **Anatomically correct form** that matches each item's how-to text and cues, which are already written in the page (`how` and `cues` fields). Someone who has never done the move should be able to copy the figure.
- **Pick the camera angle per item that shows the move best:** side, front or three-quarter. Use three-quarter or overhead where a pure side view hides the point, for example twists, side plank, butterfly, pigeon and lateral moves.
- **Feet, hands and anything else that should touch the floor actually touch it.** Nothing floats or sinks.
- **Smooth, natural motion** with easing and slight overlap: the hips lead in hinges, arms follow, and the body settles at the end of a rep.
- **Readable at small size.** The same artwork is shown as a 50×46 px list thumbnail, roughly 190–240 px in the details panel, and roughly 240 px next to the timer.

## Hard constraints

1. **No build step. Each page stays a single self-contained `.html` file.** Inline all artwork as SVG markup, JS data or `data:` URIs. Do not reference external image files or hosts.
2. **External scripts are optional.** If you use one (for example `lottie-web`), load it only from `cdnjs.cloudflare.com`, `cdn.jsdelivr.net/npm/` or `unpkg.com`, pinned to an exact version, never `@latest` or a range. Plain SVG plus JS is preferred.
3. **Size:** keep each page under about 2 MB total.
4. **Performance:** up to about 25 figures can animate at once in the list. Keep it smooth on a mid-range phone. Pause figures that aren't visible; the current code already skips hidden ones.
5. **Theming:** the pages support light and dark mode through CSS custom properties on `:root`, redefined under `@media (prefers-color-scheme: dark)` and `[data-theme="dark"]`. All figure colours must come from CSS variables so both themes work. Keep each page's palette:
   - **Circuit:** green top (`--work`), dark leggings and shoes, and orange (`--rest`) dumbbells and kettlebells.
   - **Yoga:** violet top (`--hold`), dark leggings, bare feet, and a lilac mat.
6. **Do not change** the exercise and pose ids, the generator, the timer maths or the page layout. **Do not touch** anything under `app/`, which is a separate Android app.
7. Respect `prefers-reduced-motion`. A slower or calmer loop is fine; the figures still need to show the movement.

## Integration contract (what the rest of each page calls)

You can rewrite the figure module completely, but keep these entry points and behaviours.

### Circuit Builder (`workout-planner/index.html`)

- `makeFig(id, kit)` returns an `<svg>` (or element) that loops the exercise's animation forever.
  - `kit` is the user's equipment, an array of `'db'` and/or `'kb'`.
  - If the exercise uses equipment, draw a dumbbell or kettlebell in the hands: a kettlebell if the exercise allows one and the user has one (or has no dumbbells), otherwise dumbbells.
  - How it's held depends on the exercise: in both hands, one weight held in both hands (goblet, swing, halo, twist, woodchop), or in the near hand only (single-arm moves).
- `mountFigs(root, kit)` fills every `[data-fig="<id>"]` element inside `root`.
- The timer calls `makeFig(currentExerciseId, kit)` and puts it in `#tFig` when the exercise changes.
- Supports: Wall Sit needs a wall, and Chair Tricep Dip needs a chair or bench.

### Yoga Flow Builder (`yoga-planner/index.html`)

- `makeFig(id)` and `mountFigs(root)` are used in the list and details panel. For a held pose, the loop should:
  1. start from a sensible position (standing, all fours, seated, lying on the back or lying on the front)
  2. move into the pose
  3. hold with visible breathing (a gentle chest rise and lengthen)
  4. return
  
  Flows (`catcow`, `sunsal`) loop the whole sequence.
- The **timer drives the figure frame by frame**. On every animation frame it calls `figFrame(seg, elapsedSeconds, breath)`:
  - `seg.type` is `'settle'`, `'move'`, `'hold'` or `'rest'`. `seg.id` is the pose. `seg.side` is `'right'`, `'left'` or `null`. `seg.sw` is true for a switch-sides transition.
  - `breath` runs from 0 to 1, synced to the on-screen pacer: 4 seconds in, 6 seconds out. Use it to drive the breathing during `hold` and `rest`.
  - **During `move`:** animate from the previous pose into this one over the transition time. Example: Warrior II should flow into Triangle. If a direct blend isn't sensible (different camera angle or different side), go through a neutral position.
  - **During `settle`:** start from the first pose's neutral position and move into the pose near the end.
  - **For the left side:** show the pose mirrored. The container currently gets a `.mirror` class that flips the figure horizontally.
  - **Paused timer:** the figure freezes, because `elapsedSeconds` stops advancing.
- Supports: Legs Up the Wall needs a wall.

The current implementation of all this is in the `/* ---------- Character renderer ---------- */` section and the functions around it (`createFig`, `drawChar`, `prepAnim`, `animPose`, `makeFig`, `figFrame`). Read those first to see how they're wired in.

## Circuit Builder: exercises and the motion to show

These are one rep looped unless noted. "1-sided" means the timer tells the user to switch sides halfway; showing one side is fine.

| id | Exercise | Kit | Motion |
|---|---|---|---|
| squat | Bodyweight Squat | – | Stand → hips back and down to thighs parallel, arms forward → stand |
| revlunge | Reverse Lunge | – | Stand → step one leg back, back knee hovers, front shin vertical → return; alternate legs |
| jumpsquat | Jump Squat | – | Squat → explode up, arms overhead, feet leave floor → soft landing into squat |
| bridge | Glute Bridge | – | On back, knees bent → drive hips up to a straight shoulder–knee line → lower |
| slbridge | Single-Leg Glute Bridge | – (1-sided) | As bridge with one leg extended in line with the thigh |
| wallsit | Wall Sit | – | Back flat on a wall, thighs parallel, hold with subtle breathing |
| latlunge | Lateral Lunge | – | Wide step to one side, sit hips back over the bent leg, other leg straight → return; alternate |
| calf | Calf Raise | – | Rise onto balls of the feet → slow lower |
| skater | Skater Jumps | – | Bound sideways landing on one foot, trailing leg sweeps behind → bound back |
| pushup | Push-Up | – | High plank → chest to near floor, elbows about 45° back → press up |
| pike | Pike Push-Up | – | Inverted V → bend elbows to lower the crown toward the floor → press up |
| dip | Chair Tricep Dip | – | Hands on chair edge behind, legs forward → bend elbows straight back to lower hips → press up |
| inchworm | Inchworm | – | Stand → fold, hands to floor → walk hands out to plank → walk back → stand |
| taps | Plank Shoulder Taps | – | High plank, one hand taps opposite shoulder, hips stay still; alternate |
| plank | Forearm Plank | – | Hold on forearms, straight line, subtle breathing |
| sideplank | Side Plank | – (1-sided) | Hold on one forearm, body sideways and stacked, top arm up (needs a front or three-quarter view so it differs from plank) |
| deadbug | Dead Bug | – | On back, arms up, knees at 90° → lower opposite arm and leg → return; alternate |
| bicycle | Bicycle Crunch | – | On back, shoulders lifted, hands behind head; elbow toward opposite knee while the other leg extends; alternate (rotation must be visible) |
| hollow | Hollow Hold | – | Low back down, shoulders and legs lifted, arms overhead; small hold |
| climbers | Mountain Climbers | – | High plank, knees drive alternately toward chest, fast |
| superman | Superman Hold | – | Face down, lift arms, chest and legs → lower |
| burpee | Burpee | – | Stand → squat, hands down → jump back to plank → jump in → jump up, arms overhead |
| jacks | Jumping Jacks | – | Front view: feet out and arms overhead → back together |
| highknees | High Knees | – | Running on the spot, knees to hip height, arms pumping |
| bear | Bear Crawl | – | Hands and toes, knees hovering; crawl forward and back with opposite hand and foot together |
| goblet | Goblet Squat | db/kb | Weight held vertically at the chest; squat with elbows inside knees |
| rdl | Romanian Deadlift | db/kb | Soft knees, hips back, weight slides down the thighs to mid-shin, flat back → hips forward to stand |
| wlunge | Weighted Reverse Lunge | db/kb | Reverse lunge holding weights at the sides |
| sumo | Sumo Squat | db/kb | Wide stance, toes out, one weight hanging between legs; squat straight down |
| deadlift | Kettlebell Deadlift | kb | Bell between the feet; hinge and grab, stand tall, lower |
| floorpress | Dumbbell Floor Press | db | On back, knees bent; press dumbbells up from elbows touching the floor |
| row | Bent-Over Row | db/kb | Hinged about 45°, flat back; pull weights to the lower ribs |
| press | Overhead Press | db/kb | Weights at shoulders → press overhead → lower |
| curl | Bicep Curl | db | Elbows pinned; curl to shoulders → lower slowly |
| lateral | Lateral Raise | db | Front view: arms raise out to shoulder height → lower |
| halo | Kettlebell Halo | kb | Bell held upside down by the horns, circled around the head; must clearly read as a circle around the head |
| kbrow | Single-Arm Row | kb/db (1-sided) | Staggered hinge, free hand on front knee; row to the hip |
| twist | Weighted Russian Twist | db/kb | Seated, leaning back, feet down or lifted; weight moves hip to hip with visible torso rotation |
| chop | Woodchop | db/kb (1-sided) | Weight low by one hip → diagonally up across the body above the opposite shoulder, back foot pivots |
| suitcase | Suitcase March | db/kb (1-sided) | Weight in one hand at the side; slow high-knee march, no leaning |
| swing | Kettlebell Swing | kb | Hike between the legs (hinge) → hip snap, bell floats to chest height |
| thruster | Thruster | db/kb | Front squat with weights at shoulders → drive up straight into overhead press |
| renegade | Renegade Row | db | High plank on dumbbells; row one, then the other |
| cleanpress | Clean & Press | kb/db (1-sided) | Hinge → clean the weight to the shoulder → press overhead → lower |
| highpull | Sumo Deadlift High Pull | kb | Wide deadlift → pull the handle to the chin, elbows high and wide |
| manmaker | Squat to Curl & Press | db | Squat with weights at the sides → stand and curl → press overhead → reverse |

## Yoga Flow Builder: poses and the motion to show

For held poses the loop is: start position, move in, breathe, return. The timer handles the transitions between poses as described above. "1-sided" poses are shown on the right, and the timer mirrors them for the left.

| id | Pose | Sanskrit | Start from | Notes |
|---|---|---|---|---|
| easyseat | Easy Seat | Sukhasana | – (breathing only) | Cross-legged, hands on knees, tall spine; should fill the frame |
| mountain | Mountain | Tadasana | – (breathing only) | Front view, standing tall |
| child | Child's Pose | Balasana | All fours | Hips to heels, chest on thighs, forehead down, arms forward; must clearly read as Child's Pose |
| catcow | Cat–Cow | Marjaryasana–Bitilasana | – (flow) | All fours; spine arches (belly down, gaze up) ↔ rounds (chin tuck); show a curved spine, not a straight bar |
| sunsal | Sun Salutation A | Surya Namaskar A | – (flow) | Mountain → arms up → fold → halfway lift → plank → chaturanga → upward dog/cobra → downward dog → step forward → fold → rise up |
| downdog | Downward Dog | Adho Mukha Svanasana | All fours | Inverted V, long spine, heels reaching down |
| fold | Standing Forward Fold | Uttanasana | Standing | Hinge from hips, head hangs, hands to floor or shins |
| lowlunge | Low Lunge | Anjaneyasana | Standing | Front knee over ankle, back knee down, arms overhead (1-sided) |
| crescent | High Lunge | Ashta Chandrasana | Standing | Back heel lifted, back leg straight, arms up (1-sided) |
| warrior1 | Warrior I | Virabhadrasana I | Standing | Back foot flat at 45°, hips forward, arms up (1-sided) |
| warrior2 | Warrior II | Virabhadrasana II | Standing (front view) | Wide stance, front knee bent over ankle, arms out level, gaze over the front hand (1-sided) |
| triangle | Triangle | Trikonasana | Standing (front view) | Both legs straight, torso tips sideways over the front leg, lower hand to shin, top arm up, one vertical arm line (1-sided) |
| sideangle | Extended Side Angle | Utthita Parsvakonasana | Standing (front view) | From Warrior II, forearm on front thigh, top arm over the ear in one line from the back heel (1-sided) |
| chair | Chair | Utkatasana | Standing | Knees bent, hips back, arms up alongside ears |
| goddess | Goddess | Utkata Konasana | Standing (front view) | Wide squat, toes out, arms in cactus shape |
| plank | Plank | Phalakasana | All fours | Straight line on hands |
| tree | Tree | Vrksasana | Standing (front view) | Foot on the inner calf or thigh of the standing leg, knee out, hands at heart or overhead; face visible (1-sided) |
| warrior3 | Warrior III | Virabhadrasana III | Standing | Torso and back leg parallel to the floor, arms forward (1-sided) |
| dancer | Dancer | Natarajasana | Standing | Hand holds the back foot, leg kicks up and back, chest tips forward, other arm reaches (1-sided) |
| cobra | Cobra | Bhujangasana | Lying face down | Chest lifts, elbows bent and hugged in, hips down |
| sphinx | Sphinx | Salamba Bhujangasana | Lying face down | On forearms, elbows under shoulders |
| locust | Locust | Salabhasana | Lying face down | Chest, arms and legs lift |
| boat | Boat | Navasana | Seated, legs long | V-shape balance on sit bones, shins or legs lifted, arms forward |
| seatedfold | Seated Forward Fold | Paschimottanasana | Seated, legs long | Fold over straight legs, hands to shins or feet |
| pigeon | Pigeon | Eka Pada Rajakapotasana | All fours | Front shin across the mat, back leg long behind; needs a three-quarter view to read (1-sided) |
| butterfly | Butterfly | Baddha Konasana | Seated | Soles together, knees open; front or three-quarter view |
| bridge | Bridge | Setu Bandha Sarvangasana | Lying on back | Hips lift, shoulders down |
| supinetwist | Supine Twist | Supta Matsyendrasana | Lying on back | Knee falls across the body, arm out, gaze away; needs an overhead or three-quarter view (1-sided) |
| happybaby | Happy Baby | Ananda Balasana | Lying on back | Knees toward armpits, hands hold the outside of the feet, soles up |
| kneeschest | Knees to Chest | Apanasana | Lying on back | Hug both knees in, gentle rock |
| legsup | Legs Up the Wall | Viparita Karani | Lying on back | Back on the floor, legs straight up resting on a wall |
| savasana | Final Rest | Savasana | – (breathing only) | Lying flat, arms slightly away, palms up, very slow breathing |

## Deliverables

1. Updated `workout-planner/index.html` and `yoga-planner/index.html` with the new figures, using the integration contract above.
2. A review page, `figures-review.html` at the repo root, showing every exercise and pose from both pages in a grid. Each one should be animating live and also shown as static key frames, in both light and dark mode, at thumbnail size and panel size.
3. A short note in the pull request listing any item you couldn't get right, and why.

## Acceptance checklist

- [ ] Every one of the 46 exercises and 32 poses has artwork and animation. No placeholders.
- [ ] Each figure matches its how-to text and would be safe to copy (knees over toes, flat back in hinges, and so on).
- [ ] The same character appears throughout each page, with consistent proportions, colours and line weight.
- [ ] No floating or sinking: contact points sit on the floor or mat.
- [ ] Readable at 50×46 px.
- [ ] Light and dark mode both look right, with all colours taken from CSS variables.
- [ ] Equipment matches the user's kit in the circuit page, and is held the right way for each exercise.
- [ ] In the yoga timer, the figure moves from one pose into the next during transitions, mirrors for left-side holds, breathes in time with the pacer and freezes when paused.
- [ ] No console errors, no horizontal scroll at 400 px wide, and smooth with about 25 figures animating.
- [ ] Each page is still a single file, with no new external hosts.
