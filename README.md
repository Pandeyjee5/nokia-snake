# Snake — Nokia-style nostalgia edition

A simple monochrome Snake game inspired by classic Nokia phones. It uses a chunky LCD-style palette, hard walls, keypad controls, swipe controls, score/high score, levels, pause, vibration feedback, and a minimal old-phone interface.

## Run in Android Studio

1. Open this `snake-game` folder in Android Studio.
2. Let Gradle sync.
3. Enable USB debugging on your Android phone.
4. Connect the phone by USB.
5. Select the phone and press Run.

The project uses Android Gradle Plugin 8.5.2, compile SDK 35, Java 17, and has no third-party game dependencies.

## Controls

- Swipe: move the snake.
- On-screen arrows: move the snake.
- Android D-pad: supported if available.
- Tap the board while playing: pause.
- Press `P`: pause/resume when using a keyboard.
- Eat food to grow and increase your score.
- Hitting a wall or yourself ends the game.

## Build APK on GitHub (no Android Studio required)

1. Create a GitHub repository and upload the contents of this project folder.
2. Push the `main` branch.
3. Open the repository's **Actions** tab and select **Build Snake APK**.
4. After the workflow finishes, open the run and download the **Nokia-Snake-APK** artifact.
5. Extract the artifact and install `app-debug.apk` on your Android phone.
