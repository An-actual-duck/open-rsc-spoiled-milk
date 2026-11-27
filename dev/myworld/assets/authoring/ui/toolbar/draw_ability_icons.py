"""Original 32-pixel ability toolbar artwork. Run with Python and Pillow."""

from pathlib import Path

from PIL import Image, ImageDraw


ICON_SIZE = 32


def wizard_hat(draw: ImageDraw.ImageDraw) -> None:
    outline = "#292337"
    draw.polygon(
        [(4, 24), (6, 21), (10, 20), (12, 14), (14, 8), (18, 4),
         (25, 4), (28, 7), (23, 7), (21, 10), (21, 14), (24, 20),
         (28, 22), (29, 25), (25, 28), (10, 28), (5, 26)],
        fill=outline,
    )
    draw.polygon(
        [(11, 20), (14, 11), (16, 7), (19, 5), (24, 5), (21, 7),
         (19, 11), (20, 16), (22, 21), (17, 23)],
        fill="#57477e",
    )
    draw.polygon([(14, 16), (16, 9), (19, 6), (22, 5), (19, 9),
                  (18, 15), (17, 17)], fill="#8876b3")
    draw.polygon([(11, 19), (15, 20), (20, 20), (22, 18), (23, 21),
                  (20, 23), (15, 23), (10, 22)], fill="#dca447")
    draw.line([(12, 20), (16, 21), (20, 21)], fill="#f7d487")
    draw.polygon([(6, 23), (9, 22), (14, 24), (20, 24), (25, 22),
                  (28, 24), (25, 26), (11, 27), (7, 26)], fill="#635187")
    draw.line([(8, 24), (12, 26), (19, 26), (24, 25)], fill="#9a8bc1")


def glint(draw: ImageDraw.ImageDraw, x: int, y: int, reach: int) -> None:
    draw.polygon(
        [(x, y - reach), (x + 2, y - 2), (x + reach, y), (x + 2, y + 2),
         (x, y + reach), (x - 2, y + 2), (x - reach, y), (x - 2, y - 2)],
        fill="#bc751b",
    )
    core = reach - 2
    draw.polygon(
        [(x, y - core), (x + 1, y - 1), (x + core, y), (x + 1, y + 1),
         (x, y + core), (x - 1, y + 1), (x - core, y), (x - 1, y - 1)],
        fill="#ffe467",
    )
    draw.point([(x, y), (x, y - 1), (x - 1, y)], fill="#fffce2")


def golden_buff(draw: ImageDraw.ImageDraw) -> None:
    glint(draw, 18, 12, 8)
    glint(draw, 8, 19, 4)
    glint(draw, 24, 22, 4)
    draw.point([(10, 7), (22, 4), (27, 9), (5, 13), (19, 24),
                (13, 27), (28, 25), (8, 26)], fill="#ffd75b")
    draw.point([(13, 9), (21, 19), (15, 22), (26, 26)], fill="#fff4be")
    draw.line([(14, 24), (15, 25)], fill="#c08423")
    draw.point((15, 24), fill="#fcb73f")


def offering_bowl(draw: ImageDraw.ImageDraw) -> None:
    draw.polygon(
        [(11, 19), (9, 16), (10, 13), (12, 12), (13, 15), (14, 10),
         (17, 5), (18, 3), (20, 7), (20, 11), (23, 13), (24, 17),
         (22, 20)],
        fill="#692f31",
    )
    draw.polygon(
        [(12, 18), (11, 16), (12, 14), (14, 16), (15, 11), (18, 6),
         (19, 9), (18, 12), (21, 13), (23, 17), (21, 19)],
        fill="#e56e2c",
    )
    draw.polygon(
        [(14, 19), (13, 17), (16, 16), (16, 13), (18, 10), (18, 14),
         (20, 15), (21, 18), (19, 19)],
        fill="#ffc052",
    )
    draw.polygon([(16, 19), (16, 17), (18, 15), (20, 18), (19, 19)],
                 fill="#fff3b3")

    outline = "#3b2828"
    draw.polygon(
        [(4, 18), (27, 18), (28, 20), (25, 24), (21, 27), (19, 27),
         (20, 29), (12, 29), (13, 27), (10, 27), (7, 24), (4, 21)],
        fill=outline,
    )
    draw.polygon(
        [(6, 20), (26, 20), (24, 23), (20, 26), (12, 26), (9, 24)],
        fill="#a76f49",
    )
    draw.polygon([(10, 22), (23, 22), (20, 24), (13, 25)],
                 fill="#d29a62")
    draw.line([(5, 19), (26, 19)], fill="#efc18a")
    draw.line([(6, 20), (25, 20)], fill="#b98357")
    draw.line([(13, 28), (19, 28)], fill="#b98357")
    draw.point([(9, 10), (24, 8)], fill="#ffd078")


def claw_marks(draw: ImageDraw.ImageDraw) -> None:
    for x, y in [(3, 7), (10, 5), (17, 3)]:
        draw.polygon(
            [(x, y + 19), (x + 1, y + 13), (x + 4, y + 7), (x + 8, y + 2),
             (x + 12, y), (x + 9, y + 5), (x + 7, y + 11),
             (x + 4, y + 17), (x + 1, y + 22)],
            fill="#42283a",
        )
        draw.polygon(
            [(x + 1, y + 18), (x + 2, y + 13), (x + 5, y + 7),
             (x + 10, y + 2), (x + 8, y + 6), (x + 6, y + 12),
             (x + 3, y + 17)],
            fill="#b85455",
        )
        draw.line([(x + 2, y + 16), (x + 4, y + 11),
                   (x + 6, y + 7), (x + 8, y + 5)], fill="#efaa93")


def main() -> None:
    destination = Path(__file__).resolve().parents[3] / "runtime" / "ui" / "toolbar"
    destination.mkdir(parents=True, exist_ok=True)
    for name, paint in (
        ("mage", wizard_hat),
        ("cleric", golden_buff),
        ("prayer", offering_bowl),
        ("summoning", claw_marks),
    ):
        image = Image.new("RGBA", (ICON_SIZE, ICON_SIZE))
        paint(ImageDraw.Draw(image))
        image.save(destination / f"{name}.png")


if __name__ == "__main__":
    main()
