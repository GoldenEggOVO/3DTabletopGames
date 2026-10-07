// Offline SVG import tool; the pack builder consumes the resulting PNG sources.
const fs = require("node:fs");
const path = require("node:path");
const {createCanvas, loadImage} = require("@napi-rs/canvas");

async function main() {
    const source = path.join(__dirname, "../../resource-pack/textures/xiangqi");
    for (const file of fs.readdirSync(source).filter(name => name.endsWith(".svg"))) {
        const svg = fs.readFileSync(path.join(source, file), "utf8")
            .replace('width="400" height="100"', 'width="1600" height="400"');
        const image = await loadImage(Buffer.from(svg));
        const canvas = createCanvas(1600, 400);
        canvas.getContext("2d").drawImage(image, 0, 0);
        fs.writeFileSync(path.join(source, file.replace(/\.svg$/, ".png")), canvas.toBuffer("image/png"));
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
