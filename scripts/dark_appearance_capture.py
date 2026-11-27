"""Capture real Android screens on a dedicated offline emulator with both debug APKs installed."""
import argparse
from pathlib import Path
import subprocess
import time

TEST_CLASS = "com.furina.classtools.DarkAppearanceCaptureTest"
RUNNER = "com.furina.classtools.debug.test/androidx.test.runner.AndroidJUnitRunner"
PACKAGE = "com.furina.classtools.debug"
FILES = "files/dark-review"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--configuration", choices=["412dp-1.0", "412dp-2.0", "800dp-1.0", "800dp-2.0"],
                        help="Run one configuration, for example on a fallback-API emulator.")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Use a dedicated review emulator. The capture seeds its debug app with local fixtures.")

    def adb(*parts):
        return subprocess.check_output([args.adb, "-s", args.serial, *parts], encoding="utf-8", errors="replace").strip()

    def wm_override(setting):
        lines = adb("shell", "wm", setting).splitlines()
        return lines[-1].split(":")[-1].strip() if len(lines) > 1 else "reset"

    width = wm_override("size")
    density = wm_override("density")
    font = adb("shell", "settings", "get", "system", "font_scale")
    if adb("shell", "settings", "get", "global", "airplane_mode_on") != "1":
        parser.error("Enable airplane mode on the review emulator to use only the offline fixtures.")
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        for dp, pixels, dpi in [(412, "824x1830", "320"), (800, "1280x1472", "256")]:
            adb("shell", "wm", "size", pixels)
            adb("shell", "wm", "density", dpi)
            for scale in ["1.0", "2.0"]:
                name = f"{dp}dp-{scale}"
                if args.configuration and name != args.configuration:
                    continue
                adb("shell", "settings", "put", "system", "font_scale", scale)
                time.sleep(1)
                report = adb("shell", "am", "instrument", "-w", "-r", "-e", "darkReview", "yes",
                             "-e", "class", TEST_CLASS, RUNNER)
                (args.output / f"{name}.log").write_text(report, encoding="utf-8")
                if "OK (1 test)" not in report:
                    raise RuntimeError(f"{name}: {report[-3000:]}")
                destination = args.output / name
                destination.mkdir(exist_ok=True)
                remote = f"{FILES}/{name}"
                names = adb("shell", "run-as", PACKAGE, "ls", remote).splitlines()
                expected = 18 if dp == 412 and scale == "1.0" else 15
                if sum(item.endswith(".png") for item in names) != expected or "scroll-gfxinfo.txt" not in names:
                    raise RuntimeError(f"Incomplete capture for {name}: {names}")
                for item in names:
                    if Path(item).name != item or (item != "scroll-gfxinfo.txt" and not item.endswith(".png")):
                        raise RuntimeError(f"Unexpected capture file: {item}")
                    with (destination / item).open("wb") as out:
                        subprocess.run([args.adb, "-s", args.serial, "exec-out", "run-as", PACKAGE,
                                        "cat", f"{remote}/{item}"], stdout=out, check=True)
                print(f"{name}: {len(list((args.output / name).glob('*.png')))} captures", flush=True)
    finally:
        adb("shell", "wm", "size", width)
        adb("shell", "wm", "density", density)
        if font == "null":
            adb("shell", "settings", "delete", "system", "font_scale")
        else:
            adb("shell", "settings", "put", "system", "font_scale", font)


if __name__ == "__main__":
    main()
