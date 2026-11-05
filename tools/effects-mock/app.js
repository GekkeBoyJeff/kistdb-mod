'use strict';
/* Libreria effects mock: the same kit, now (left) and the proposal (right). Data: SPEC (effects.json). */
const TAU = Math.PI * 2;
const R = Math.random;
const gauss = () => { let u = 0, v = 0; while (!u) u = R(); while (!v) v = R(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(TAU * v); };
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const lerp = (a, b, t) => a + (b - a) * t;
const mixc = (a, b, t) => a.map((x, i) => Math.round(lerp(x, b[i], t)));
const seq = (pre, a, b, pad = 0) => { const o = []; const s = a <= b ? 1 : -1; for (let i = a; s > 0 ? i <= b : i >= b; i += s) o.push(pre + String(i).padStart(pad, '0')); return o; };

// ------------------------------------------------------------------ sprites
const IMG = {};
for (const k in SPR) { const im = new Image(); im.src = SPR[k]; IMG[k] = im; }
const TINT = new Map();
function sprite(name, rgb) {
  const im = IMG[name];
  if (!im || !im.complete || !im.naturalWidth) return null;
  if (!rgb) return im;
  const key = name + '|' + rgb.join(',');
  let c = TINT.get(key);
  if (!c) {
    c = document.createElement('canvas'); c.width = im.naturalWidth; c.height = im.naturalHeight;
    const g = c.getContext('2d'); g.imageSmoothingEnabled = false; g.drawImage(im, 0, 0);
    g.globalCompositeOperation = 'multiply'; g.fillStyle = `rgb(${rgb[0]},${rgb[1]},${rgb[2]})`; g.fillRect(0, 0, c.width, c.height);
    g.globalCompositeOperation = 'destination-in'; g.drawImage(im, 0, 0);
    TINT.set(key, c);
  }
  return c;
}

// ------------------------------------------------------------------ particle types (approximations of the vanilla ones)
const GEN = seq('generic_', 7, 0);
const SGA = 'abcdefghijklmnopqrstuvwxyz'.split('').map(c => 'sga_' + c);
const flameLike = (spr, w) => ({ spr: [spr], w, life: () => 8 / (R() * 0.8 + 0.2) + 4, fric: 0.96, grav: 0, shrink: 1, bright: 1, jitter: 0.004 });
const PT = {
  flame: flameLike('flame', 0.17), small_flame: flameLike('flame', 0.09), soul_fire_flame: flameLike('soul_fire_flame', 0.17),
  copper_fire_flame: flameLike('copper_fire_flame', 0.17),
  lava: { spr: ['lava'], w: 0.16, life: () => 16 / (R() * 0.8 + 0.2), grav: 0.75, fric: 0.999, bright: 1, shrink: 1 },
  electric_spark: { spr: ['glow'], col: () => [255, 245, 215], w: 0.09, life: () => 4 + R() * 5, fric: 0.75, bright: 1 },
  flash: { spr: ['flash'], w: 1.4, life: () => 4, bright: 1, fade: 1 },
  snowflake: { anim: GEN, col: () => [245, 250, 255], w: 0.13, life: () => 20 / (R() * 0.8 + 0.2), grav: 0.25, fric: 1, sway: 0.006 },
  dust: { anim: GEN, w: 0.13, life: () => 8 / (R() * 0.8 + 0.2) + 6, fric: 0.96, grav: 0 },
  dust_color_transition: { anim: GEN, w: 0.13, life: () => 18 + R() * 8, fric: 0.96, grav: 0, transition: 1 },
  end_rod: { anim: seq('glitter_', 7, 0), col: () => [255, 255, 255], w: 0.11, life: () => 50 + R() * 12, fric: 0.91, grav: -0.02, bright: 1, fade: 1 },
  soul: { anim: seq('soul_', 0, 10), w: 0.28, life: () => 22 + R() * 6, fric: 0.96, grav: -0.015, bright: 1 },
  sculk_soul: { anim: seq('sculk_soul_', 0, 10), w: 0.28, life: () => 22 + R() * 6, fric: 0.96, grav: -0.015, bright: 1 },
  sculk_charge_pop: { anim: seq('sculk_charge_pop_', 0, 3), w: 0.18, life: () => 8, fric: 0.9, bright: 1 },
  enchant: { sprR: SGA, col: () => { const v = Math.round(230 + R() * 25); return [v, v, v]; }, w: 0.09, life: () => 30 + R() * 10, mode: 'approach', bright: 1 },
  nautilus: { spr: ['nautilus'], w: 0.1, life: () => 30 + R() * 10, mode: 'approach', bright: 1 },
  portal: { sprR: GEN, col: () => { const f = R() * 0.6 + 0.4; return [Math.round(230 * f), Math.round(77 * f), Math.round(255 * f)]; }, w: 0.08, life: () => 40 + R() * 10, mode: 'approach' },
  reverse_portal: { sprR: GEN, col: () => { const f = R() * 0.6 + 0.4; return [Math.round(230 * f), Math.round(77 * f), Math.round(255 * f)]; }, w: 0.08, life: () => 40 + R() * 10, fric: 0.96 },
  crit: { spr: ['critical_hit'], col: () => { const v = Math.round(180 + R() * 75); return [v, v, v]; }, w: 0.14, life: () => 6 + R() * 6, fric: 0.7, grav: 0.4 },
  enchanted_hit: { spr: ['enchanted_hit'], col: () => [77, 205, 255], w: 0.14, life: () => 6 + R() * 6, fric: 0.7, grav: 0.4, bright: 1 },
  witch: { anim: seq('spell_', 7, 0), col: () => [Math.round(170 + R() * 40), 60, Math.round(200 + R() * 40)], w: 0.14, life: () => 10 + R() * 8, fric: 0.96, grav: -0.1 },
  white_ash: { spr: ['generic_0'], col: () => [175, 175, 175], w: 0.05, life: () => 40 + R() * 20, fric: 0.98, grav: 0.02, sway: 0.008 },
  happy_villager: { spr: ['glint'], w: 0.14, life: () => 20 + R() * 10, fric: 0.96, grav: 0, bright: 1 },
  cloud: { anim: GEN, col: () => [255, 255, 255], w: 0.45, life: () => 12 / (R() * 0.8 + 0.2), fric: 0.96, grav: -0.03, fade: 1 },
  poof: { anim: GEN, col: () => [255, 255, 255], w: 0.32, life: () => 10 / (R() * 0.8 + 0.2), fric: 0.9, grav: 0, fade: 1 },
  white_smoke: { anim: GEN, col: () => [250, 250, 250], w: 0.38, life: () => 22 + R() * 10, fric: 0.97, grav: -0.04, fade: 1 },
  smoke: { anim: GEN, col: () => { const v = Math.round(60 + R() * 40); return [v, v, v]; }, w: 0.22, life: () => 12 + R() * 8, fric: 0.96, grav: -0.05, fade: 1 },
  bubble: { spr: ['bubble'], w: 0.07, life: () => 8 / (R() * 0.8 + 0.2), fric: 0.85, grav: -0.5 },
  bubble_pop: { anim: seq('bubble_pop_', 0, 4), w: 0.1, life: () => 8, fric: 0.9 },
  splash: { sprR: seq('splash_', 0, 3), col: () => [90, 140, 255], w: 0.09, life: () => 8 / (R() * 0.8 + 0.2), grav: 0.8, fric: 0.98 },
  dripping_water: { spr: ['drip_hang'], col: () => [60, 100, 255], w: 0.07, life: () => 40, mode: 'drip' },
  falling_honey: { spr: ['drip_fall'], col: () => [235, 140, 30], w: 0.07, life: () => 30, grav: 0.3, fric: 0.98 },
  noxious_gas: { anim: seq('noxious_gas_', 1, 8, 2), w: 0.75, life: () => 40 + R() * 10, fric: 0.96, grav: -0.01, fade: 1 },
  small_gust: { anim: seq('small_gust_', 0, 6), w: 0.45, life: () => 12, fric: 0.9 },
  gust: { anim: seq('small_gust_', 0, 6), w: 1.1, life: () => 12, fric: 0.9 },
  firework: { anim: seq('spark_', 7, 0), col: () => [255, 255, 255], w: 0.1, life: () => 12 + R() * 6, fric: 0.91, grav: 0.1, bright: 1 },
  wax_off: { spr: ['glow'], col: () => [255, 255, 255], w: 0.09, life: () => 10 + R() * 8, fric: 0.96, bright: 1 },
  wax_on: { spr: ['glow'], col: () => [255, 160, 50], w: 0.09, life: () => 10 + R() * 8, fric: 0.96, bright: 1 },
  glow: { spr: ['glow'], col: () => [150, 255, 210], w: 0.11, life: () => 30 + R() * 10, fric: 0.96, bright: 1 },
  damage_indicator: { spr: ['damage'], w: 0.14, life: () => 20, fric: 0.9, grav: 0.4 },
  trail: { spr: ['generic_0'], w: 0.07, life: () => 10, mode: 'trail', bright: 1 },
  sweep_attack: { anim: seq('sweep_', 0, 7), col: () => [230, 230, 230], w: 1.2, life: () => 4, flat: 1 },
  cherry_leaves: { sprR: seq('cherry_', 0, 11), w: 0.12, life: () => 70, fric: 0.98, grav: 0.08, sway: 0.012 },
  tinted_leaves: { sprR: seq('leaf_', 0, 11), w: 0.12, life: () => 70, fric: 0.98, grav: 0.08, sway: 0.012 },
  squid_ink: { anim: GEN, col: () => [22, 22, 30], w: 0.22, life: () => 20, fric: 0.96, grav: 0.1 },
  item_slime: { spr: ['generic_3'], col: () => [110, 200, 90], w: 0.07, life: () => 16, grav: 0.6, fric: 0.98 },
  falling_dust: { anim: GEN, w: 0.1, life: () => 40, grav: 0.12, fric: 0.98 },
  dolphin: { spr: ['generic_0'], col: () => [90, 140, 255], w: 0.06, life: () => 22, fric: 0.96 },
  shriek: { spr: ['shriek'], w: 0.5, life: () => 25, grav: -0.08, fric: 1, fade: 1, flat: 1 },
  bubble_column_up: { spr: ['bubble'], w: 0.07, life: () => 20, grav: -0.3, fric: 0.9 }
};
const TYPE_ALIAS = { generic: 'dust' };

// ------------------------------------------------------------------ the spec
const FAM = SPEC.families, ORDER = SPEC.order, BY_ENCHANT = {};
for (const f in FAM) for (const e of FAM[f].enchants) BY_ENCHANT[e] = f;
const MAXLV = { protection: 4, fire_protection: 4, blast_protection: 4, projectile_protection: 4, feather_falling: 4, respiration: 3, aqua_affinity: 1,
  thorns: 3, depth_strider: 3, frost_walker: 2, soul_speed: 3, swift_sneak: 3, sharpness: 5, smite: 5, bane_of_arthropods: 5, knockback: 2,
  fire_aspect: 2, looting: 3, sweeping_edge: 3, efficiency: 5, silk_touch: 1, unbreaking: 3, fortune: 3, power: 5, punch: 2, flame: 1, infinity: 1,
  luck_of_the_sea: 3, lure: 3, loyalty: 3, impaling: 5, riptide: 3, channeling: 1, multishot: 1, quick_charge: 3, piercing: 4, mending: 1, density: 5,
  breach: 4, wind_burst: 3, lunge: 3, binding_curse: 1, vanishing_curse: 1 };
const ENCH_NAME = e => e.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ').replace('Of', 'of').replace('The', 'the');
const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];
function tierOf(ench, lv) {
  const max = MAXLV[ench] || 1, t = lv / max;
  if (t > 1) return 'overcharged';
  if (t >= 1) return 'max';
  return t <= SPEC.tiers.hint.upTo ? 'hint' : 'clear';
}
const TIER_IX = { hint: 0, clear: 1, max: 2, overcharged: 3 };
const SLOTS = ['head', 'chest', 'legs', 'feet'];

/** Families on an item, most striking first, with the best tier per family. */
function famsOf(item) {
  if (!item) return [];
  const best = {};
  for (const [e, lv] of Object.entries(item.ench || {})) {
    const f = BY_ENCHANT[e]; if (!f) continue;
    const tr = tierOf(e, lv);
    if (!best[f] || TIER_IX[tr] > TIER_IX[best[f].tier]) best[f] = { fam: f, tier: tr, ench: e, lv };
  }
  return Object.values(best).sort((a, b) => ORDER.indexOf(a.fam) - ORDER.indexOf(b.fam));
}
const visible = x => !FAM[x.fam].silent && !FAM[x.fam].accentOnly;

// ------------------------------------------------------------------ worlds and particles
function makeWorld() { return { parts: [], t: 0, run: 0, credits: new Map(), shots: [] }; }
const PLAYER = {
  y: 0, vy: 0, walking: false, sneaking: false, water: false, burning: false, swing: 0, hurt: 0, airborne: false, fallStart: 0,
  stepPhase: 0, walkT: 0
};
let INTENSITY = 'normal', KIT = null;

/** Player frame (feet at origin, facing +z) → world. */
function W(x, y, z) { return [x, y + PLAYER.y, z]; }
function bodyH() { return PLAYER.sneaking ? 1.5 : 1.8; }
function yScale() { return PLAYER.sneaking ? 0.84 : 1; }

function spawn(world, type, pos, o = {}) {
  const T = PT[TYPE_ALIAS[type] || type]; if (!T) return;
  const n = o.count || 1;
  for (let i = 0; i < n; i++) {
    const sp = o.spread || 0, sv = o.speed || 0;
    const p = { T, type, age: 0, life: (T.life() * (o.lifeMul || 1)) | 0, x: pos[0] + gauss() * sp, y: pos[1] + gauss() * sp * 0.7, z: pos[2] + gauss() * sp,
      vx: (o.v ? o.v[0] : 0) + gauss() * sv, vy: (o.v ? o.v[1] : 0) + gauss() * sv, vz: (o.v ? o.v[2] : 0) + gauss() * sv,
      w: T.w * (o.size || 1) * (0.85 + R() * 0.3), col: o.color || (T.col ? T.col() : null), to: o.to || null,
      spr: T.sprR ? T.sprR[(R() * T.sprR.length) | 0] : null, seed: R() * TAU };
    if (T.mode === 'approach') {
      // flies in from a little way out and settles on the point
      const off = o.from || [gauss() * 0.5, R() * 0.5 + 0.2, gauss() * 0.5];
      p.ox = p.x; p.oy = p.y; p.oz = p.z; p.dx = off[0]; p.dy = off[1]; p.dz = off[2];
      p.x += off[0]; p.y += off[1]; p.z += off[2];
    }
    if (T.mode === 'trail') { p.sx = p.x; p.sy = p.y; p.sz = p.z; p.life = o.duration || 10; }
    if (T.mode === 'drip') { p.hang = 8 + (R() * 6 | 0); }
    world.parts.push(p);
  }
}

function tickParticles(world) {
  const out = [];
  for (const p of world.parts) {
    p.age++;
    if (p.age >= p.life) continue;
    const T = p.T, f = p.age / p.life;
    if (T.mode === 'approach') {
      const g = 1 - f, f4 = Math.pow(f, 4);
      p.x = p.ox + p.dx * g; p.y = p.oy + p.dy * g - f4 * 0.4; p.z = p.oz + p.dz * g;
    } else if (T.mode === 'trail') {
      p.x = lerp(p.sx, p.to[0], f); p.y = lerp(p.sy, p.to[1], f); p.z = lerp(p.sz, p.to[2], f);
    } else if (T.mode === 'drip') {
      if (p.age > p.hang) { p.vy -= 0.02; p.y += p.vy; if (!p.fell) { p.fell = 1; } }
    } else {
      p.vy -= 0.04 * (T.grav || 0);
      if (T.sway) { p.vx += Math.sin(p.age * 0.3 + p.seed) * T.sway * 0.3; p.vz += Math.cos(p.age * 0.27 + p.seed) * T.sway * 0.3; }
      if (T.jitter) { p.vx += gauss() * T.jitter; p.vz += gauss() * T.jitter; }
      p.x += p.vx; p.y += p.vy; p.z += p.vz;
      const fr = T.fric ?? 0.98; p.vx *= fr; p.vy *= fr; p.vz *= fr;
      if (p.y < 0.01 && (T.grav || 0) > 0) { p.y = 0.01; p.vy = 0; p.vx *= 0.7; p.vz *= 0.7; }
    }
    out.push(p);
  }
  world.parts = out;
  // projectiles with a trail
  world.shots = world.shots.filter(s => {
    s.age++;
    s.x += s.vx; s.y += s.vy; s.z += s.vz; s.vy -= 0.02;
    for (const t of s.trail) spawn(world, t.p, [s.x, s.y, s.z], { color: t.color, spread: 0.03, size: 0.9, to: [s.x - s.vx, s.y - s.vy, s.z - s.vz], duration: 6 });
    return s.age < 26 && s.y > 0;
  });
}

// ------------------------------------------------------------------ the held item
const ITEM_LOOK = { sword: ['#7FE8E0', '#6B4A2B', 0.62], axe: ['#7FE8E0', '#6B4A2B', 0.55], pickaxe: ['#7FE8E0', '#6B4A2B', 0.55], shovel: ['#7FE8E0', '#6B4A2B', 0.55],
  hoe: ['#7FE8E0', '#6B4A2B', 0.55], trident: ['#4FB3A0', '#4FB3A0', 0.95], spear: ['#C9C9C9', '#6B4A2B', 0.95], mace: ['#8A8A8A', '#5A4A3A', 0.55],
  bow: ['#7A5230', '#7A5230', 0.6], crossbow: ['#6B4A2B', '#6B4A2B', 0.5], fishing_rod: ['#7A5230', '#7A5230', 0.8], book: ['#7B3FA0', '#7B3FA0', 0.25], other: ['#999', '#999', 0.3] };
/** The hand and the item's direction, in the player frame. */
function handFrame() {
  const sw = PLAYER.swing > 0 ? Math.sin((1 - PLAYER.swing / 6) * Math.PI) : 0;
  const walk = PLAYER.walking ? Math.sin(PLAYER.walkT * 0.6) * 0.35 : 0;
  const a = -0.35 - sw * 1.4 + walk; // arm pitch (rad), forward is negative
  const s = 0.9375 / 16, ys = yScale();
  const sh = [5 * s, 22 * s * ys, 0];
  const hand = [sh[0] + 0.02, sh[1] - Math.cos(a) * 10 * s, sh[2] - Math.sin(a) * 10 * s];
  const dir = [0.28, Math.sin(-a) * 0.55 + 0.45, Math.cos(-a) * 0.75 + 0.25];
  const l = Math.hypot(...dir); return { hand, dir: dir.map(d => d / l), arm: a };
}
function anchorPoint(kind, t) {
  const { hand, dir } = handFrame();
  const L = (ITEM_LOOK[kind] || ITEM_LOOK.other)[2];
  const a = (SPEC.items[kind] || SPEC.items.other).anchor;
  const u = a === 'blade' ? 0.3 + R() * 0.7 : a === 'head' ? 0.8 + R() * 0.2 : a === 'tip' ? 0.92 + R() * 0.08 : a === 'string' ? R() : 0.2;
  const p = [hand[0] + dir[0] * L * u, hand[1] + dir[1] * L * u, hand[2] + dir[2] * L * u];
  if (a === 'string') p[1] += (R() - 0.5) * 0.5;
  return p;
}

// ------------------------------------------------------------------ motions
function vel(motion, rel, speed = 1) {
  const r = Math.hypot(rel[0], rel[2]) || 1, rx = rel[0] / r, rz = rel[2] / r;
  switch (motion) {
    case 'rise': return [gauss() * 0.004, 0.03 * speed, gauss() * 0.004];
    case 'fall': return [gauss() * 0.004, -0.03 * speed, gauss() * 0.004];
    case 'sink': return [0, -0.015 * speed, 0];
    case 'float': return [gauss() * 0.01, gauss() * 0.01, gauss() * 0.01].map(v => v * speed);
    case 'pop': return [gauss() * 0.03, 0.1 * speed, gauss() * 0.03];
    case 'sway': return [0, -0.012 * speed, 0];
    case 'tangent': return [-rz * 0.05 * speed, 0.004, rx * 0.05 * speed];
    case 'outward': return [rx * 0.08 * speed, 0.01, rz * 0.08 * speed];
    default: return [0, 0, 0];
  }
}

/** One family's look at a point (relative to the feet). */
function emitMotif(world, famId, at, o = {}) {
  const F = FAM[famId]; if (!F || !F.motif) return;
  let m = F.motif;
  let p = m.p;
  if (PLAYER.water && m.underwater) p = m.underwater;
  const motion = o.motion || m.motion;
  const color = o.color || (m.color ? hex(m.color) : null);
  const size = (m.size || 1) * (o.size || 1);
  const pos = W(...at);
  if (motion === 'approach') spawn(world, p, pos, { color, size, count: o.count || 1, from: [at[0] * 0.8 + gauss() * 0.15, 0.25 + R() * 0.3, at[2] * 0.8 + gauss() * 0.15] });
  else spawn(world, p, pos, { color, size, count: o.count || 1, spread: o.spread ?? 0.04, v: vel(motion, at, o.speed || 1), speed: 0.004 });
}
function emitPart(world, part, at, o = {}) {
  // part: {p, color, motion, size}
  const color = part.color === 'trim' ? o.trimColor : part.color ? hex(part.color) : o.color || null;
  const motion = part.motion || o.motion || (PT[part.p] && PT[part.p].mode === 'approach' ? 'approach' : 'float');
  const pos = W(...at);
  if (part.p === 'dust_color_transition') { spawn(world, 'dust_color_transition', pos, { color: hex(part.color || '#FFFFFF'), spread: 0.04, v: vel('float', at) }); return; }
  if (motion === 'approach') spawn(world, part.p, pos, { color, size: part.size || 1, from: [at[0] * 0.8, 0.3 + R() * 0.3, at[2] * 0.8] });
  else spawn(world, part.p, pos, { color, size: (part.size || 1) * (o.size || 1), spread: 0.05, v: vel(motion, at, o.speed || 1), speed: 0.004 });
}

// ------------------------------------------------------------------ shapes (points relative to the feet)
function shapePoint(shape, z, t, k, opt = {}) {
  const ys = yScale();
  const y = (z.y ?? 1.2) * ys, r = z.r ?? 0.5;
  switch (shape) {
    case 'sentinels': { const a = (k % 4) * TAU / 4 + Math.PI / 4; return [Math.cos(a) * r * 1.15, y, Math.sin(a) * r * 1.15]; }
    case 'pulse': { const a = t * 1.6 + k * 2.399, rr = r * (0.65 + 0.35 * Math.sin(t * 5)); return [Math.cos(a) * rr, y, Math.sin(a) * rr]; }
    case 'square': { const s = ((t * 0.5 + k * 0.618) % 1) * 4, side = s | 0, u = s - side, h = r * 0.9;
      const pts = [[-h + 2 * h * u, -h], [h, -h + 2 * h * u], [h - 2 * h * u, h], [-h, h - 2 * h * u]][side]; return [pts[0], y, pts[1]]; }
    case 'spiral': { const y0 = (z.y0 ?? Math.max(0.2, y - 0.35)) * ys, y1 = (z.y1 ?? y + 0.35) * ys; const u = (t * 0.45 + k * 0.071) % 1, a = u * TAU * 2 + t;
      return [Math.cos(a) * r, lerp(y0, y1, u), Math.sin(a) * r]; }
    case 'helix': { const y0 = (z.y0 ?? 0.22) * ys, y1 = (z.y1 ?? 1.95) * ys; const u = (t * 0.28) % 1, a = u * TAU * 2.5 + (k % 2) * Math.PI;
      return [Math.cos(a) * r, lerp(y0, y1, u), Math.sin(a) * r]; }
    case 'column': { if (z.y0 != null && z.y1 != null && z.center) return [gauss() * 0.06, lerp(z.y0, z.y1, R()) * ys, gauss() * 0.06];
      const a = k * 2.399; return [Math.cos(a) * r, y - 0.15 + R() * 0.3, Math.sin(a) * r]; }
    case 'ribs': { const a = (k % 6) * TAU / 6 + 0.3; return [Math.cos(a) * r, y - 0.3 + R() * 0.6, Math.sin(a) * r]; }
    case 'zigzag': { const a = t * 1.3 + k * 1.9, rr = r * ((k % 2) ? 0.7 : 1.15); return [Math.cos(a) * rr, y + ((t * 2 + k * 0.37) % 1 - 0.5) * 0.5, Math.sin(a) * rr]; }
    case 'cloud': return [gauss() * 0.22, (z.y ?? 2.35) * ys + gauss() * 0.05, gauss() * 0.22];
    case 'front': { const a = Math.PI / 2 + (R() - 0.5) * 1.2; return [Math.cos(a) * r, y, Math.sin(a) * r]; }
    default: { const a = t * 0.9 + k * 2.399; return [Math.cos(a) * r, y, Math.sin(a) * r]; }
  }
}

// ------------------------------------------------------------------ the kit → what you see (the proposal)
function trimColor(piece) { return piece && piece.trim ? hex(SPEC.trimMaterials[piece.trim.material].color) : null; }
function materialColor(piece) {
  if (!piece || !piece.material) return null;
  const M = SPEC.materials[piece.material]; if (!M) return null;
  if (M.color === 'dye') return hex(piece.dye || M.default);
  return hex(M.accent || M.color);
}
function accentColor(piece) { return trimColor(piece) || materialColor(piece); }

function countBy(fn) { const c = {}; for (const s of SLOTS) { const v = fn(KIT[s], s); if (v) c[v] = (c[v] || 0) + 1; } return c; }
function kitFamilies() {
  const all = {};
  for (const s of [...SLOTS, 'hand', 'off']) for (const x of famsOf(KIT[s])) {
    const e = all[x.fam] || (all[x.fam] = { fam: x.fam, pieces: 0, tiers: [] });
    if (SLOTS.includes(s)) e.pieces++;
    e.tiers.push(x.tier);
  }
  return all;
}
function condFam(c, fams) {
  if (typeof c === 'string') return !!fams[c];
  const e = fams[c.family]; if (!e) return false;
  if (c.pieces && e.pieces < c.pieces) return false;
  if (c.tier && !e.tiers.every(t => TIER_IX[t] >= TIER_IX[c.tier])) return false;
  return true;
}
function condMat(when) {
  if (when.material) { const c = countBy(p => p && p.material); for (const [m, n] of Object.entries(when.material)) if ((c[m] || 0) < n) return false; }
  if (when.trimPattern) { const c = countBy(p => p && p.trim && p.trim.pattern); for (const [m, n] of Object.entries(when.trimPattern)) if ((c[m] || 0) < n) return false; }
  return true;
}
function comboMatches(cb, fams) {
  const w = cb.when;
  if (w.all && !w.all.every(c => condFam(c, fams))) return false;
  if (w.any && !w.any.some(c => condFam(c, fams))) return false;
  if (!condMat(w)) return false;
  if (w.anyOf && !w.anyOf.some(condMat)) return false;
  if (w.maxedPieces) {
    let n = 0; for (const s of SLOTS) { const f = famsOf(KIT[s]); if (f.length >= 3 && f.every(x => TIER_IX[x.tier] >= 2)) n++; }
    if (n < w.maxedPieces) return false;
  }
  if (w.sameMaterial) { const c = countBy(p => p && p.material); if (Math.max(0, ...Object.values(c)) < w.sameMaterial) return false; }
  if (w.sameTrimPattern) { const c = countBy(p => p && p.trim && p.trim.pattern); if (Math.max(0, ...Object.values(c)) < w.sameTrimPattern) return false; }
  return true;
}
function comboFamilies(cb) {
  const out = new Set(); const add = c => out.add(typeof c === 'string' ? c : c.family);
  (cb.when.all || []).forEach(add); (cb.when.any || []).forEach(add); return out;
}

const RATE_MUL = { subtle: 0.6, normal: 1, rich: 1.6 };
const TIER_RATE = t => SPEC.tiers[t].rate, TIER_SIZE = t => SPEC.tiers[t].size;

/** Builds the list of emitters for the kit: what the proposal shows, and a description of every layer. */
function composeProposal() {
  const em = [], notes = [];
  const fams = kitFamilies();
  // 9. combinations first: they say which parts are taken
  const taken = new Set(), noMatSet = new Set(), noTrimSet = new Set(), perClass = {};
  const combos = [];
  for (const cb of SPEC.combos) {
    if (combos.length >= SPEC.comboRules.atOnce.total) break;
    if ((perClass[cb.class] || 0) >= SPEC.comboRules.atOnce[cb.class]) continue;
    if (!comboMatches(cb, fams)) continue;
    combos.push(cb); perClass[cb.class] = (perClass[cb.class] || 0) + 1;
    comboFamilies(cb).forEach(f => fams[f] && taken.add(f));
    if (cb.when.material) Object.keys(cb.when.material).forEach(m => noMatSet.add(m));
    (cb.when.anyOf || []).forEach(a => { if (a.trimPattern) Object.keys(a.trimPattern).forEach(p => noTrimSet.add(p)); if (a.material) Object.keys(a.material).forEach(m => noMatSet.add(m)); });
    if (cb.when.trimPattern) Object.keys(cb.when.trimPattern).forEach(p => noTrimSet.add(p));
    if (cb.when.sameMaterial) Object.keys(SPEC.materials).forEach(m => noMatSet.add(m));
    if (cb.when.sameTrimPattern) Object.keys(SPEC.trimPatterns).forEach(p => noTrimSet.add(p));
  }
  const firstTrim = SLOTS.map(s => trimColor(KIT[s])).find(Boolean) || [255, 230, 140];
  for (const cb of combos) {
    const L = cb.look, z = { y: L.y, y0: L.y0, y1: L.y1, r: L.r ?? SPEC.zones.aura.r, center: L.shape === 'column' && L.y0 != null };
    let k = 0;
    em.push({ id: 'combo:' + cb.id, prio: 0, rate: 4, emit(world, t) {
      k++;
      if (L.shape === 'anchor') { if (KIT.hand) emitPart(world, L, anchorPoint(KIT.hand.kind, t), { trimColor: firstTrim }); return; }
      if (L.shape === 'burst') { // portal glyphs pulled into the eyes
        const at = [0, (L.y ?? 1.7) * yScale(), 0.1];
        spawn(world, L.p, W(...at), { from: [gauss() * 0.8, gauss() * 0.5, gauss() * 0.8] }); return; }
      const sh = L.shape === 'ring' && L.motion === 'tangent' ? 'ring' : L.shape;
      const at = shapePoint(sh, { ...z, y: L.y ?? (L.shape === 'ring' ? 1.2 : undefined) }, t, k);
      const p = L.alternate && k % 2 ? L.alternate : L.p;
      emitPart(world, { ...L, p, motion: L.motion }, at, { trimColor: firstTrim, count: L.n || 1 });
      if (L.shape === 'helix') { const at2 = shapePoint('helix', z, t, k + 1); emitPart(world, { ...L, p: L.alternate ? (k % 2 ? L.p : L.alternate) : L.p }, at2, { trimColor: firstTrim }); }
      for (const ex of L.extra || []) {
        if (ex.share != null && R() > ex.share) continue;
        const sh2 = ex.shape || sh;
        const at3 = ex.shape ? shapePoint(ex.shape, { y: ex.y ?? L.y, r: z.r * (ex.shape === 'ring' && ex.y ? 0.55 : 1), y0: z.y0, y1: z.y1 }, t, k * 3 + 1) : at;
        emitPart(world, ex, at3, { trimColor: firstTrim, motion: ex.motion || (ex.outward ? 'outward' : undefined) });
      }
    } });
    notes.push(['Combinatie', `<b>${cb.name}</b> <span class=dim>(${comboWhy(cb)})</span>: vervangt de losse looks ervan`]);
  }

  // 8a. family set: all four armour pieces share a family (not Protection, not curses, not silent)
  const pieceFams = SLOTS.map(s => famsOf(KIT[s]));
  let setFam = null;
  if (pieceFams.every(l => l.length)) {
    for (const x of pieceFams[0]) {
      if (FAM[x.fam].silent || FAM[x.fam].accentOnly || taken.has(x.fam)) continue;
      if (pieceFams.every(l => l.some(y => y.fam === x.fam))) { setFam = x.fam; break; }
    }
  }
  if (setFam) {
    const tier = pieceFams.map(l => l.find(y => y.fam === setFam).tier).sort((a, b) => TIER_IX[a] - TIER_IX[b])[0];
    let k = 0;
    em.push({ id: 'set:' + setFam, prio: 2, rate: 5 * TIER_RATE(tier), emit(world, t) {
      k++; const z = SPEC.zones.body;
      for (const s of [0, 1]) { const at = shapePoint('helix', { y0: z.y0, y1: z.y1, r: z.r }, t, k * 2 + s); emitMotif(world, setFam, at, { size: TIER_SIZE(tier) * 1.3, spread: 0.01, motion: FAM[setFam].motif.motion === 'approach' ? 'approach' : 'still' }); }
      if (TIER_IX[tier] >= 2 && FAM[setFam].flourish && R() < FAM[setFam].flourish.share) emitPart(world, FAM[setFam].flourish, shapePoint('helix', { y0: z.y0, y1: z.y1, r: z.r }, t, k));
    } });
    notes.push(['Set', `vier stukken met <b>${FAM[setFam].name}</b>: een helix om je lijf (${tier})`]);
  }

  // 8b. material set, 8c. trim set
  const mats = countBy(p => p && p.material), pats = countBy(p => p && p.trim && p.trim.pattern);
  for (const [m, n] of Object.entries(mats)) {
    if (n < 4 || noMatSet.has(m) || !SPEC.materials[m].set) continue;
    const S = SPEC.materials[m].set; let k = 0;
    em.push({ id: 'matset:' + m, prio: 4, rate: (S.rate || 1) * 1.5, when: S.when, emit(world, t) {
      k++;
      if (S.when === 'walking' && !PLAYER.walking) return;
      if (S.when === 'moving' && !PLAYER.walking) return;
      const color = S.color === 'dye' ? materialColor(KIT.chest) : S.color ? hex(S.color) : null;
      if (S.shape === 'footprint') { const side = k % 2 ? 1 : -1; spawn(world, S.p, W(side * 0.13, 0.04, 0.05), { color, spread: 0.06, count: 3, size: 1.3 }); return; }
      const at = shapePoint(S.shape, { y: S.y ?? 1.2, r: 0.5 }, t, k);
      if (S.p === 'dust_color_transition') { spawn(world, 'dust_color_transition', W(...at), { color: hex(S.color), to: hex(S.to), spread: 0.05, v: vel('float', at) }); return; }
      spawn(world, S.p, W(...at), { color, spread: 0.05, v: vel(S.motion || 'float', at) });
      if (S.extra && R() < S.extra.share) spawn(world, S.extra.p, W(...at), { v: vel('pop', at) });
    } });
    notes.push(['Materiaalset', `vier stukken <b>${SPEC.materials[m].name}</b>: ${S.name}`]);
  }
  for (const [pat, n] of Object.entries(pats)) {
    if (n < 4 || noTrimSet.has(pat)) continue;
    const S = SPEC.trimPatterns[pat].set; let k = 0;
    const cols = SLOTS.map(s => trimColor(KIT[s])).filter(Boolean);
    em.push({ id: 'trimset:' + pat, prio: 3, rate: 2.5, emit(world, t) {
      k++; const color = S.color ? hex(S.color) : cols[k % cols.length];
      if (S.when === 'walking' && !PLAYER.walking) return;
      if (S.shape === 'footprint') { const side = k % 2 ? 1 : -1; spawn(world, S.p, W(side * 0.13, 0.04, 0.05), { color, spread: 0.05, count: 2 }); return; }
      if (S.shape === 'trail') { const a = t * 2.4 + (k % S.n) * TAU / S.n, r = 0.55, y = S.circle * yScale();
        const from = [Math.cos(a) * r, y, Math.sin(a) * r], to = [Math.cos(a + 0.9) * r, y + 0.05, Math.sin(a + 0.9) * r];
        spawn(world, 'trail', W(...from), { color, to: W(...to), duration: 8 }); return; }
      if (S.shape === 'burst') { spawn(world, S.p, W(0, (S.y || 1.7) * yScale(), 0.1), { from: [gauss() * 0.8, gauss() * 0.5, gauss() * 0.8] }); return; }
      const z = { y: S.y ?? 1.2, r: 0.55, y0: S.y0, y1: S.y1, center: S.shape === 'column' };
      const at = shapePoint(S.shape, z, t, k);
      const pp = S.p === 'falling_dust' ? 'falling_dust' : S.p;
      spawn(world, pp, W(...at), { color: S.p === 'falling_dust' ? (color || [219, 207, 163]) : (['dust', 'tinted_leaves', 'trail'].includes(S.p) ? color : null), spread: 0.04,
        v: vel(S.motion || (S.shape === 'column' ? 'rise' : 'float'), at) });
      if (S.full) for (let i = 1; i < 4; i++) { const a2 = shapePoint('ring', { y: z.y, r: 0.5 }, t, k + i * 0.25 * (TAU / 2.399)); spawn(world, S.p, W(...a2), { color }); }
      if (S.also) { const a3 = shapePoint('ring', { y: S.also.y, r: 0.3 }, t, k); spawn(world, S.p, W(...a3), { color }); }
      if (S.extra) spawn(world, S.extra.p, W(...at), { color: S.extra.color ? hex(S.extra.color) : null, spread: 0.1 });
    } });
    notes.push(['Trimset', `vier keer het patroon <b>${pat}</b>: ${S.name}, in de trimkleur(en)`]);
  }

  // 1–7. each piece in its zone; the hand along its item
  SLOTS.forEach((s, i) => {
    const piece = KIT[s]; if (!piece) return;
    let list = pieceFams[i].filter(x => !taken.has(x.fam) && x.fam !== setFam && !FAM[x.fam].silent);
    const vis = list.filter(visible);
    const primary = vis[0] || (list.length && list.every(x => FAM[x.fam].accentOnly) && !setFam ? list[0] : null);
    const secondary = vis[0] ? list.filter(x => x !== primary && !FAM[x.fam].silent)[0] : null;
    const zone = SPEC.zones[s];
    const pat = piece.trim ? SPEC.trimPatterns[piece.trim.pattern] : null;
    const shape = pat ? pat.shape : 'ring', pmotion = pat && pat.motion;
    const color = accentColor(piece);
    const ch = SPEC.materials[piece.material] ? SPEC.materials[piece.material].character : { speed: 1, size: 1 };
    const hush = pat && pat.hush ? pat.hush : 1;
    const z = { y: zone.y, r: zone.r, y0: zone.y - 0.35, y1: zone.y + 0.35 };
    if (shape === 'column' && s === 'head') { z.y0 = 2.0; z.y1 = 2.5; }
    if (primary) {
      let k = i * 7;
      const tier = primary.tier;
      em.push({ id: 'zone:' + s, prio: 5, rate: 2 * TIER_RATE(tier) * (pat && pat.rate ? pat.rate : 1) * hush, emit(world, t) {
        k++;
        const sh = shape === 'ring' && pat && pat.front ? 'front' : shape;
        const at = shapePoint(sh, z, t, k);
        const motion = pmotion || undefined;
        const at2 = shapePoint(sh, z, t - 0.12, k + (sh === 'ribs' || sh === 'sentinels' ? 0 : 0.18));
        emitMotif(world, primary.fam, at, { size: TIER_SIZE(tier) * ch.size, speed: ch.speed * 0.5, motion });
        // a short stroke of the shape, so the shape reads (a square looks square, ribs look like ribs)
        emitMotif(world, primary.fam, at2, { size: TIER_SIZE(tier) * ch.size * 0.85, speed: ch.speed * 0.5, motion });
        // a trim draws its shape in its own colour; the enchantment rides along
        if (piece.trim) for (const q of [at, at2]) spawn(world, 'dust', W(q[0] * 1.08, q[1], q[2] * 1.08), { color: trimColor(piece), size: 0.95, v: vel(pmotion || 'float', q, 0.4) });
        if (pat && pat.full) for (let j = 1; j < 4; j++) emitMotif(world, primary.fam, shapePoint('ring', z, t, k + j * 0.6556), { size: 0.8 });
        const F = FAM[primary.fam];
        if (TIER_IX[tier] >= 2 && F.flourish && R() < F.flourish.share * (tier === 'overcharged' ? 2 : 1)) emitPart(world, F.flourish, at, { color });
        if (tier === 'overcharged' && color) spawn(world, 'dust', W(...at), { color, spread: 0.08, count: 2 });
      } });
      notes.push([cap(s), `<b>${FAM[primary.fam].name}</b> <span class=dim>(${ENCH_NAME(primary.ench)} ${ROMAN[primary.lv] || primary.lv}, ${primary.tier})</span> · ${ZONE_NL[s]}${pat ? ` · vorm: <b>${shape}</b> (trim ${piece.trim.pattern})` : ''}`]);
    } else if (pieceFams[i].length && !pieceFams[i].some(x => taken.has(x.fam) || x.fam === setFam)) {
      notes.push([cap(s), '<span class=dim>alleen stille enchantments (Unbreaking, Mending): geen eigen look</span>']);
    } else if (pieceFams[i].some(x => taken.has(x.fam) || x.fam === setFam)) {
      notes.push([cap(s), '<span class=dim>zit in de set of combinatie</span>']);
    }
    if (secondary) {
      let k = i * 11;
      em.push({ id: 'acc:' + s, prio: 6, rate: 2 * TIER_RATE(primary.tier) / 3 * hush, emit(world, t) {
        k++; const at = shapePoint(shape, z, t, k + 0.5);
        emitMotif(world, secondary.fam, at, { size: 0.9 * TIER_SIZE(secondary.tier) * ch.size, speed: ch.speed, motion: pmotion });
      } });
      notes.push(['', `↳ tweede: <b>${FAM[secondary.fam].name}</b> <span class=dim>(een derde zo vaak, op dezelfde plek)</span>`]);
    }
    if (color && (primary || piece.trim)) {
      const tm = piece.trim ? SPEC.trimMaterials[piece.trim.material] : null;
      const sp = tm ? tm.sparkle : (SPEC.materials[piece.material] && SPEC.materials[piece.material].character.sparkle ? { p: SPEC.materials[piece.material].character.sparkle } : { p: 'dust' });
      let k = i * 13;
      em.push({ id: 'sparkle:' + s, prio: 7, rate: primary ? 0.6 : 0.9, emit(world, t) {
        k++; const at = shapePoint(shape, z, t, k + 0.25);
        const c = sp.color ? hex(sp.color) : color;
        if (sp.p === 'enchant') spawn(world, 'enchant', W(...at), { from: [at[0] * 0.6, 0.3, at[2] * 0.6] });
        else spawn(world, sp.p, W(...at), { color: ['dust', 'trail'].includes(sp.p) ? c : null, spread: 0.05, v: vel('float', at), size: 0.8 });
      } });
      notes.push(['', `↳ kleur: <span class=sw style="background:rgb(${color})"></span> ${piece.trim ? `trim ${piece.trim.material}` : (piece.material === 'leather' ? 'leerkleur' : SPEC.materials[piece.material].name)}${sp ? `, glinster: ${sp.p}` : ''}`]);
    }
  });
  for (const hs of ['hand', 'off']) {
    const it = KIT[hs]; if (!it) continue;
    const list = famsOf(it).filter(x => !taken.has(x.fam) && !FAM[x.fam].silent);
    const primary = list.filter(visible)[0] || null, secondary = primary ? list.filter(x => x !== primary)[0] : null;
    if (!primary) { if (famsOf(it).some(x => taken.has(x.fam))) notes.push([hs === 'hand' ? 'Hand' : 'Tweede hand', '<span class=dim>zit in de combinatie</span>']); continue; }
    let k = 0;
    em.push({ id: hs, prio: 1, rate: 3 * TIER_RATE(primary.tier) * (it.kind === 'book' ? 0.5 : 1) * (hs === 'off' ? 0.5 : 1), emit(world, t) {
      k++; if (hs === 'off') return; // the mock draws only the main hand's item
      const at = anchorPoint(it.kind, t);
      const use = secondary && k % 3 === 0 ? secondary : primary;
      emitMotif(world, use.fam, at, { size: TIER_SIZE(primary.tier) * 0.9, spread: 0.02, motion: FAM[use.fam].motif.motion === 'rise' ? 'rise' : undefined });
      const F = FAM[primary.fam];
      if (TIER_IX[primary.tier] >= 2 && F.flourish && R() < F.flourish.share) emitPart(world, F.flourish, at);
    } });
    notes.push([hs === 'hand' ? 'Hand' : 'Tweede hand', `<b>${FAM[primary.fam].name}</b> <span class=dim>(${ENCH_NAME(primary.ench)} ${ROMAN[primary.lv] || primary.lv}, ${primary.tier})</span> langs ${(SPEC.items[it.kind] || SPEC.items.other).anchor}${secondary ? ` · tweede: ${FAM[secondary.fam].name}` : ''}`]);
  }
  return { em, notes };
}
const ZONE_NL = { head: 'halo boven je hoofd', chest: 'baan om je borst', legs: 'om je heupen', feet: 'om je enkels' };
const cap = s => ({ head: 'Helm', chest: 'Borst', legs: 'Benen', feet: 'Laarzen' })[s] || s;
function comboWhy(cb) {
  const w = cb.when, out = [];
  const f = c => typeof c === 'string' ? FAM[c].name : `${FAM[c.family].name}${c.pieces ? ' op ' + c.pieces : ''}${c.tier ? ' (' + c.tier + ')' : ''}`;
  if (w.all) out.push(w.all.map(f).join(' + '));
  if (w.any) out.push(w.any.length > 1 ? '(' + w.any.map(f).join(' of ') + ')' : f(w.any[0]));
  if (w.material) out.push(Object.entries(w.material).map(([m, n]) => `${n}× ${SPEC.materials[m].name}`).join(' + '));
  if (w.trimPattern) out.push(Object.entries(w.trimPattern).map(([m, n]) => `trim ${m}`).join(' + '));
  if (w.anyOf) out.push(w.anyOf.map(a => a.trimPattern ? 'trim ' + Object.keys(a.trimPattern)[0] : SPEC.materials[Object.keys(a.material)[0]].name).join(' of '));
  if (w.maxedPieces) out.push('alles maxed, één materiaal, één trimpatroon');
  return out.join(' + ');
}

// ------------------------------------------------------------------ the current system (a port of Aura.compose and Aura.send, as others see you)
const NOW_HEIGHT = [0.95, 0.7, 0.4, 0.05];
const NOW_TINY = new Set(['protection', 'feather', 'haste']);
const NOW_PART = { fire: 'small_flame', lightning: 'electric_spark', frost: 'snowflake', soul: 'soul_fire_flame', holy: 'end_rod', poison: 'item_slime',
  tide: 'dolphin', water: 'splash', sculk: 'sculk_soul', blast: 'smoke', sweep: 'cloud', feather: 'white_ash', loyalty: 'glow', sharp: 'crit', impale: 'nautilus',
  heavy: 'reverse_portal', breach: 'lava', power: 'enchanted_hit', multishot: 'firework', haste: 'wax_off', wind: 'small_gust', infinity: 'witch',
  thorns: 'damage_indicator', fireguard: 'flame', arrowguard: 'crit', protection: 'enchant', silk: 'cherry_leaves', curse: 'squid_ink', luck: 'happy_villager', sea: 'bubble_pop' };
const NOW_BUSY = new Set(['breach', 'multishot', 'thorns', 'sweep', 'curse', 'blast', 'water', 'wind', 'sea']);
function nowEffects(item) { return famsOf(item).filter(x => NOW_PART[x.fam]).map(x => ({ fam: x.fam, st: x.tier === 'hint' ? 1 : x.tier === 'clear' ? 2 : 3 })); }
function composeNow() {
  const max = { subtle: 1, normal: 2, rich: 3 }[INTENSITY];
  const out = [];
  const slow = (f, e) => NOW_BUSY.has(f) ? e * 2 : e;
  const hand = nowEffects(KIT.hand)[0];
  if (hand) { const st = Math.min(max, hand.st); out.push({ kind: 'HAND', fam: hand.fam, h: 0.55, every: slow(hand.fam, st >= 3 ? 1 : st === 2 ? 2 : 4) * (KIT.hand.kind === 'book' ? 2 : 1), st }); }
  const armour = SLOTS.map(s => nowEffects(KIT[s]));
  let set = null;
  if (armour.every(l => l.length)) for (const e of armour[0]) { if (armour.every(l => l.some(x => x.fam === e.fam))) { set = { fam: e.fam, st: Math.min(...armour.map(l => l.find(x => x.fam === e.fam).st)) }; break; } }
  if (set) { const st = Math.min(max, set.st); out.push({ kind: 'SET', fam: set.fam, h: 0.5, every: slow(set.fam, st === 1 ? 2 : 1), st }); }
  else {
    const hs = {}, str = {};
    armour.forEach((l, i) => { const e = l[0]; if (!e) return; (hs[e.fam] = hs[e.fam] || []).push(NOW_HEIGHT[i]); str[e.fam] = Math.max(str[e.fam] || 0, e.st); });
    const fams = Object.keys(hs).sort((a, b) => (ORDER_NOW.indexOf(a) - ORDER_NOW.indexOf(b)) || (hs[b].length - hs[a].length));
    let n = 0;
    for (const f of fams) { if (n === 2) break; if (hand && f === hand.fam) continue; n++;
      const st = Math.min(max, str[f]); out.push({ kind: 'ARMOUR', fam: f, h: hs[f].reduce((a, b) => a + b, 0) / hs[f].length, every: slow(f, st >= 3 ? 2 : st === 2 ? 4 : 8), st }); }
  }
  return out.slice(0, 3);
}
const ORDER_NOW = Object.keys(SPEC.families);
function emitNow(world) {
  const run = world.run, h = bodyH();
  composeNow().forEach((s, idx) => {
    const ord = { HAND: 0, SET: 1, ARMOUR: 2 }[s.kind];
    if ((run + 17 + ord) % s.every !== 0) return;
    const p = NOW_PART[s.fam];
    const count = NOW_TINY.has(s.fam) ? 3 : 1;
    if (s.kind === 'HAND') {
      const pos = W(0.35, h * s.h, 0.4);
      if (PT[p].mode === 'approach') spawn(world, p, pos, { count, from: [gauss() * 0.3, 0.3, gauss() * 0.3] });
      else spawn(world, p, pos, { count, spread: 0.12, speed: 0.01 });
    } else if (s.kind === 'ARMOUR') {
      const pos = W(0, h * s.h, 0);
      if (PT[p].mode === 'approach') spawn(world, p, pos, { count, from: [gauss() * 0.3, 0.3, gauss() * 0.3] });
      else spawn(world, p, pos, { count, spread: 0.15, speed: 0.01 });
    } else {
      const points = s.st >= 3 ? 2 : 1;
      for (let i = 0; i < points; i++) {
        const a = (run * 40 + i * 180) * Math.PI / 180, y = h * (0.5 + 0.42 * Math.sin(run * 0.23 + i * Math.PI));
        const pos = W(Math.cos(a) * 0.6, y, Math.sin(a) * 0.6);
        if (PT[p].mode === 'approach') spawn(world, p, pos, { count, from: [gauss() * 0.3, 0.3, gauss() * 0.3] });
        else spawn(world, p, pos, { count: count === 1 ? 1 : count, spread: count === 1 ? 0 : 0.06 });
      }
    }
  });
}

// ------------------------------------------------------------------ the budget (the proposal)
function emitProposal(world, comp) {
  const per = SPEC.budget.pointsPerRun[INTENSITY], mul = RATE_MUL[INTENSITY];
  const t = world.t / 20;
  for (const e of comp.em) {
    const c = (world.credits.get(e.id) || Math.random()) + e.rate * mul * SPEC.budget.runTicks / 20;
    world.credits.set(e.id, Math.min(c, 3));
  }
  const ready = comp.em.filter(e => world.credits.get(e.id) >= 1).sort((a, b) => (world.credits.get(b.id) - world.credits.get(a.id)) || a.prio - b.prio);
  for (const e of ready.slice(0, per)) { world.credits.set(e.id, world.credits.get(e.id) - 1); e.emit(world, t); }
}

// ------------------------------------------------------------------ events (the proposal; the current system has none)
function handFam() { const l = KIT.hand ? famsOf(KIT.hand).filter(visible) : []; return l[0] || null; }
function armourHas(fam) { return SLOTS.filter(s => famsOf(KIT[s]).some(x => x.fam === fam)).length; }
function event(world, name) {
  const yS = yScale();
  if (name === 'swing') {
    const hf = handFam(); if (!hf) return;
    const ev = FAM[hf.fam].events && FAM[hf.fam].events.swing;
    const p = ev ? ev.p : FAM[hf.fam].motif.p, color = ev && ev.color ? hex(ev.color) : (FAM[hf.fam].motif.color ? hex(FAM[hf.fam].motif.color) : null);
    if (ev && ev.p === 'sweep_attack') { later(world, 2, () => spawn(world, 'sweep_attack', W(0, 1.1 * yS, 1.0))); }
    for (let i = 0; i < 9; i++) later(world, (i / 3) | 0, () => {
      const a = Math.PI / 2 + (i / 8 - 0.5) * 2.0;
      spawn(world, p === 'sweep_attack' ? 'cloud' : p, W(Math.cos(a) * 1.05, (1.3 - i * 0.04) * yS, Math.sin(a) * 1.05), { color, size: p === 'sweep_attack' ? 0.4 : 1.1, v: [Math.cos(a) * 0.01, 0, Math.sin(a) * 0.01] });
    });
  }
  if (name === 'kill') {
    const hf = handFam(); const at = W(0, 0.9, 1.8);
    if (hf) { const ev = FAM[hf.fam].events && FAM[hf.fam].events.kill; spawn(world, ev ? ev.p : FAM[hf.fam].motif.p, at, { count: 10, spread: 0.3, speed: 0.06, color: FAM[hf.fam].motif.color ? hex(FAM[hf.fam].motif.color) : null }); }
    spawn(world, 'poof', at, { count: 8, spread: 0.3, speed: 0.04 });
    if (famsOf(KIT.hand).some(x => x.fam === 'luck')) spawn(world, 'happy_villager', at, { count: 6, spread: 0.35, speed: 0.02 });
  }
  if (name === 'hurt') {
    const chestY = 1.2 * yS;
    if (armourHas('protection')) {
      spawn(world, 'enchant', W(0, chestY, 0), { count: 14, from: null });
      for (let i = 0; i < 12; i++) for (let j = 0; j < 3; j++) { const a = i / 12 * TAU; later(world, j, () => spawn(world, 'trail', W(Math.cos(a) * 0.3, chestY, Math.sin(a) * 0.3), { color: [127, 232, 255], size: 1.4, to: W(Math.cos(a) * 1.1, chestY + 0.1, Math.sin(a) * 1.1), duration: 8 })); }
    }
    if (armourHas('thorns')) for (let i = 0; i < 5; i++) { const at = [gauss() * 0.15, chestY + gauss() * 0.15, 0.3], to = W(gauss() * 0.3, 1.0 + gauss() * 0.2, 2.2);
      for (let j = 0; j < 3; j++) later(world, j, () => spawn(world, 'trail', W(...at), { color: [212, 32, 43], size: 1.5, to, duration: 7 }));
      later(world, 7, () => spawn(world, 'damage_indicator', W(gauss() * 0.2, 1.0, 2.2), { spread: 0.1 })); }
    if (armourHas('arrowguard')) for (let i = 0; i < 10; i++) { const a = i / 10 * TAU; spawn(world, 'crit', W(Math.cos(a) * 0.6, chestY, Math.sin(a) * 0.6)); }
    if (armourHas('blast')) spawn(world, 'poof', W(0, chestY, 0), { count: 12, spread: 0.1, speed: 0.08 });
  }
  if (name === 'shoot') {
    const hf = KIT.hand ? famsOf(KIT.hand).filter(x => FAM[x.fam].events && FAM[x.fam].events.shoot) : [];
    const trail = hf.slice(0, 2).map(x => ({ p: FAM[x.fam].events.shoot.p, color: FAM[x.fam].events.shoot.color ? hex(FAM[x.fam].events.shoot.color) : null }));
    const { hand } = handFrame();
    const n = famsOf(KIT.hand).some(x => x.fam === 'multishot') ? 3 : 1;
    for (let i = 0; i < n; i++) world.shots.push({ x: hand[0], y: hand[1] + PLAYER.y, z: hand[2] + 0.4, vx: (i - (n - 1) / 2) * 0.08, vy: 0.12, vz: 0.9, age: 0, trail: trail.length ? trail : [{ p: 'crit' }] });
  }
}
const LATER = [];
function later(world, ticks, fn) { LATER.push({ world, at: world.t + ticks, fn }); }

function playerEvents(world) {
  const t = world.t, yS = yScale();
  // steps
  if (PLAYER.walking && !PLAYER.airborne && t % 6 === 0) {
    const side = (t / 6) % 2 ? 1 : -1, foot = W(side * 0.13, 0.05, 0.08 + 0.15 * side);
    if (armourHas('frost')) { spawn(world, 'snowflake', foot, { count: 3, spread: 0.08, v: [0, 0.03, 0] }); spawn(world, 'dust', foot, { color: [223, 244, 255], count: 2, spread: 0.1 }); }
    if (armourHas('soul')) { spawn(world, 'soul_fire_flame', foot, { count: 1, spread: 0.05, v: [0, 0.02, 0] }); if (R() < 0.35) spawn(world, 'soul', foot, { v: [0, 0.03, 0] }); }
  }
  if (PLAYER.sneaking && armourHas('sculk') && t % 5 === 0) spawn(world, 'sculk_soul', W(...shapePoint('ribs', { y: 0.6, r: 0.4 }, t / 20, t)), { v: [0, 0.02, 0] });
  if (PLAYER.water && t % 3 === 0) {
    if (famsOf(KIT.head).some(x => x.fam === 'water')) spawn(world, 'bubble', W(0, 1.75 * yS, 0.2), { spread: 0.04, v: [0, 0.05, 0] });
    if (famsOf(KIT.feet).some(x => x.fam === 'water')) spawn(world, 'bubble', W(gauss() * 0.15, 0.15, -0.1), { spread: 0.05, v: [0, 0.05, 0] });
  }
  if (PLAYER.burning && armourHas('fireguard') && t % 4 === 0) for (let i = 0; i < 2; i++) { const a = t * 0.3 + i * Math.PI; spawn(world, 'flame', W(Math.cos(a) * 0.6, 1.0 * yS, Math.sin(a) * 0.6)); }
  if (PLAYER.airborne && PLAYER.vy < -0.05 && armourHas('feather') && t % 2 === 0) {
    const a = t * 0.7; spawn(world, 'white_ash', W(Math.cos(a) * 0.5, 0.6 + R(), Math.sin(a) * 0.5), { v: [0, 0.12, 0], size: 1.5 });
    if (R() < 0.4) spawn(world, 'cloud', W(Math.cos(a + 2) * 0.5, 0.5, Math.sin(a + 2) * 0.5), { v: [0, 0.1, 0], size: 0.35 });
  }
}

// ------------------------------------------------------------------ the scene
const S = 0.9375 / 16;
const SKIN = '#C9906C', SHIRT = '#2AA9B5', PANTS = '#3B3FA8', SHOE = '#5A5A5A', HAIR = '#3B2A1A';
function matPaint(piece, fallback) {
  if (!piece || !piece.material) return fallback;
  const m = piece.material;
  return { leather: piece.dye || '#A06540', chainmail: '#8D9398', copper: '#C9724E', iron: '#D2D2D2', gold: '#EECF3E', diamond: '#3FD3C9', netherite: '#4A4246', turtle_scute: '#4AA64A', elytra: '#7A7AA8' }[m] || fallback;
}
function cuboid(x0, y0, z0, x1, y1, z1, color, opt = {}) { return { x0, y0, z0, x1, y1, z1, color, ...opt }; }
function modelParts() {
  const walk = PLAYER.walking ? Math.sin(PLAYER.walkT * 0.6) * 0.55 : 0;
  const { arm } = handFrame();
  const ys = yScale();
  const inflate = 0.6;
  const H = KIT.head, C = KIT.chest, Lg = KIT.legs, F = KIT.feet;
  const out = [];
  const pivot = (px, py, pz, ang) => ({ pivot: [px, py, pz], ang });
  const lean = PLAYER.sneaking ? 0.45 : 0, hip = 12;
  const up = (o) => ({ ...o, lean, hip });
  // legs (leggings above, boots below)
  for (const side of [-1, 1]) {
    const x0 = side < 0 ? -4 : 0, x1 = side < 0 ? 0 : 4, a = side * walk;
    out.push(cuboid(x0, 4, -2, x1, 12, 2, Lg ? matPaint(Lg) : PANTS, { ...pivot(side * 2, 12, 0, a), slot: 'legs', inf: Lg ? inflate : 0 }));
    out.push(cuboid(x0, 0, -2, x1, 4, 2, F ? matPaint(F) : SHOE, { ...pivot(side * 2, 12, 0, a), slot: 'feet', inf: F ? inflate : 0 }));
  }
  out.push(up(cuboid(-4, 12, -2, 4, 24, 2, C ? matPaint(C) : SHIRT, { slot: 'chest', inf: C ? inflate : 0 })));
  out.push(up(cuboid(-8, 12, -2, -4, 24, 2, C ? matPaint(C) : SHIRT, { ...pivot(-6, 22, 0, -walk), slot: 'chest', inf: C ? inflate : 0 })));
  out.push(up(cuboid(4, 12, -2, 8, 24, 2, C ? matPaint(C) : SHIRT, { ...pivot(6, 22, 0, arm + 0.35), slot: 'chest', inf: C ? inflate : 0, arm: 1 })));
  out.push(up(cuboid(-4, 24, -4, 4, 32, 4, H ? matPaint(H) : SKIN, { slot: 'head', inf: H ? 0.9 : 0, face: !H })));
  return out;
}
function rotX(p, piv, a) { const y = p[1] - piv[1], z = p[2] - piv[2], c = Math.cos(a), s = Math.sin(a); return [p[0], piv[1] + y * c - z * s, piv[2] + y * s + z * c]; }
function partFaces(part) {
  const i = part.inf || 0;
  const xs = [part.x0 - i, part.x1 + i], ys = [part.y0 - i, part.y1 + i], zs = [part.z0 - i, part.z1 + i];
  const v = (a, b, c) => {
    let p = [xs[a], ys[b], zs[c]];
    if (part.pivot) p = rotX(p, part.pivot, part.ang);
    if (part.lean) p = rotX(p, [0, part.hip, 0], part.lean);
    return [p[0] * S, p[1] * S * (PLAYER.sneaking ? 0.95 : 1) + PLAYER.y - (PLAYER.sneaking ? 0.08 : 0), p[2] * S];
  };
  const F = [
    { q: [v(0, 1, 1), v(1, 1, 1), v(1, 1, 0), v(0, 1, 0)], sh: 1.0, n: [0, 1, 0] },
    { q: [v(0, 0, 0), v(1, 0, 0), v(1, 0, 1), v(0, 0, 1)], sh: 0.5, n: [0, -1, 0] },
    { q: [v(0, 0, 1), v(1, 0, 1), v(1, 1, 1), v(0, 1, 1)], sh: 0.82, n: [0, 0, 1] },
    { q: [v(1, 0, 0), v(0, 0, 0), v(0, 1, 0), v(1, 1, 0)], sh: 0.82, n: [0, 0, -1] },
    { q: [v(1, 0, 1), v(1, 0, 0), v(1, 1, 0), v(1, 1, 1)], sh: 0.64, n: [1, 0, 0] },
    { q: [v(0, 0, 0), v(0, 0, 1), v(0, 1, 1), v(0, 1, 0)], sh: 0.64, n: [-1, 0, 0] }];
  return F;
}

// ------------------------------------------------------------------ camera and drawing
const CAM = { yaw: 2.75, pitch: 0.22, dist: 3.1, target: [0, 1.05, 0] };
function camBasis() {
  const t = [CAM.target[0], CAM.target[1] + PLAYER.y * 0.6, CAM.target[2]];
  const pos = [t[0] + Math.sin(CAM.yaw) * Math.cos(CAM.pitch) * CAM.dist, t[1] + Math.sin(CAM.pitch) * CAM.dist, t[2] + Math.cos(CAM.yaw) * Math.cos(CAM.pitch) * CAM.dist];
  const f = norm(sub(t, pos)), r = norm(cross([0, 1, 0], f)), u = cross(f, r);
  return { pos, f, r, u };
}
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]], dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(x => x / l); };
function project(cv, B, p) {
  const d = sub(p, B.pos), z = dot(d, B.f); if (z < 0.05) return null;
  const fl = cv.height / (2 * Math.tan(35 * Math.PI / 180));
  return [cv.width / 2 + dot(d, B.r) * fl / z, cv.height / 2 - dot(d, B.u) * fl / z, z, fl];
}
function shade(hexc, s, tint) {
  let c = hex(hexc).map(x => x * s);
  if (tint) c = c.map((x, i) => x * (1 - tint[3]) + tint[i] * tint[3]);
  return `rgb(${c.map(x => Math.max(0, Math.min(255, Math.round(x)))).join(',')})`;
}
function draw(cv, world) {
  const g = cv.getContext('2d'); g.imageSmoothingEnabled = false;
  const B = camBasis();
  // sky and ground
  const sky = g.createLinearGradient(0, 0, 0, cv.height); sky.addColorStop(0, '#78A7FF'); sky.addColorStop(1, '#C3D8FF');
  g.fillStyle = sky; g.fillRect(0, 0, cv.width, cv.height);
  for (let x = -6; x < 6; x++) for (let z = -6; z < 6; z++) {
    const q = [[x, 0, z], [x + 1, 0, z], [x + 1, 0, z + 1], [x, 0, z + 1]].map(p => project(cv, B, p));
    if (q.some(v => !v)) continue;
    g.fillStyle = (x + z) & 1 ? '#5E9E3A' : '#649F3F'; g.beginPath(); q.forEach((v, i) => i ? g.lineTo(v[0], v[1]) : g.moveTo(v[0], v[1])); g.closePath(); g.fill();
  }
  // a shadow
  const sh = project(cv, B, [0, 0.005, 0]);
  if (sh) { g.fillStyle = 'rgba(0,0,0,.22)'; g.beginPath(); g.ellipse(sh[0], sh[1], 0.35 * sh[3] / sh[2], 0.18 * sh[3] / sh[2], 0, 0, TAU); g.fill(); }
  const items = [];
  const hurtTint = PLAYER.hurt > 0 ? [255, 40, 40, 0.45 * PLAYER.hurt / 10] : null;
  const glintT = performance.now() / 1000;
  for (const part of modelParts()) {
    const piece = part.slot ? KIT[part.slot] : null;
    const enchanted = piece && Object.keys(piece.ench || {}).length;
    for (const face of partFaces(part)) {
      const c = face.q.reduce((a, p) => [a[0] + p[0] / 4, a[1] + p[1] / 4, a[2] + p[2] / 4], [0, 0, 0]);
      const e1 = sub(face.q[1], face.q[0]), e2 = sub(face.q[3], face.q[0]), n = norm(cross(e2, e1));
      if (dot(n, sub(B.pos, c)) <= 0) continue;
      const P = face.q.map(p => project(cv, B, p)); if (P.some(v => !v)) continue;
      const depth = Math.hypot(...sub(c, B.pos));
      items.push({ depth, draw() {
        const shL = face.sh * (0.92 + 0.08 * n[1]);
        g.fillStyle = shade(part.color, shL, hurtTint);
        g.beginPath(); P.forEach((v, i) => i ? g.lineTo(v[0], v[1]) : g.moveTo(v[0], v[1])); g.closePath(); g.fill();
        if (piece && piece.trim) { g.strokeStyle = `rgb(${trimColor(piece).join(',')})`; g.lineWidth = Math.max(1, 1.6 * P[0][3] / P[0][2] * 0.02); g.stroke(); }
        if (enchanted) { const a = 0.10 + 0.08 * Math.sin(glintT * 3 + c[1] * 4 + c[0] * 3); g.fillStyle = `rgba(170,90,255,${a})`; g.fill(); }
        if (part.face && face.n[2] === 1) { // a face, so you can tell front from back
          const m = (a, b) => [lerp(P[0][0], P[1][0], a) * (1 - b) + lerp(P[3][0], P[2][0], a) * b, lerp(P[0][1], P[1][1], a) * (1 - b) + lerp(P[3][1], P[2][1], a) * b];
          g.fillStyle = '#fff'; for (const ex of [0.25, 0.75]) { const e = m(ex - 0.06, 0.45), e2 = m(ex + 0.06, 0.55); g.fillRect(e[0], e[1], e2[0] - e[0], e2[1] - e[1]); }
        }
      } });
    }
  }
  // the held item
  if (KIT.hand) {
    const { hand, dir } = handFrame(); const L = (ITEM_LOOK[KIT.hand.kind] || ITEM_LOOK.other);
    const a = W(...hand), b = W(hand[0] + dir[0] * L[2], hand[1] + dir[1] * L[2], hand[2] + dir[2] * L[2]);
    const pa = project(cv, B, a), pb = project(cv, B, b);
    if (pa && pb) items.push({ depth: Math.hypot(...sub(a, B.pos)) - 0.05, draw() {
      g.lineCap = 'square';
      g.strokeStyle = L[1]; g.lineWidth = Math.max(2, 0.05 * pa[3] / pa[2]); g.beginPath(); g.moveTo(pa[0], pa[1]); g.lineTo(lerp(pa[0], pb[0], 0.3), lerp(pa[1], pb[1], 0.3)); g.stroke();
      g.strokeStyle = L[0]; g.lineWidth = Math.max(2, 0.07 * pa[3] / pa[2]); g.beginPath(); g.moveTo(lerp(pa[0], pb[0], 0.3), lerp(pa[1], pb[1], 0.3)); g.lineTo(pb[0], pb[1]); g.stroke();
      if (Object.keys(KIT.hand.ench || {}).length) { g.strokeStyle = `rgba(190,120,255,${0.25 + 0.15 * Math.sin(glintT * 4)})`; g.stroke(); }
    } });
  }
  // arrows
  for (const s of world.shots) { const p = project(cv, B, [s.x, s.y, s.z]), q = project(cv, B, [s.x - s.vx * 0.4, s.y - s.vy * 0.4, s.z - s.vz * 0.4]);
    if (p && q) items.push({ depth: p[2], draw() { g.strokeStyle = '#ddd'; g.lineWidth = 2; g.beginPath(); g.moveTo(p[0], p[1]); g.lineTo(q[0], q[1]); g.stroke(); } }); }
  // particles
  for (const p of world.parts) {
    const pr = project(cv, B, [p.x, p.y, p.z]); if (!pr) continue;
    const T = p.T, f = p.age / p.life;
    let name = p.spr || (T.anim ? T.anim[Math.min(T.anim.length - 1, (f * T.anim.length) | 0)] : T.spr[0]);
    if (T.mode === 'drip' && p.fell) name = 'drip_fall';
    let col = p.col;
    if (T.transition && p.to) col = mixc(p.col, p.to, f);
    const im = sprite(name, col); if (!im) continue;
    let w = p.w * pr[3] / pr[2];
    if (T.shrink) w *= 1 - f * f * 0.5;
    const alpha = T.fade ? Math.min(1, (1 - f) * 2) : (f > 0.85 ? (1 - f) / 0.15 : 1);
    items.push({ depth: pr[2], draw() { g.globalAlpha = alpha; g.drawImage(im, pr[0] - w / 2, pr[1] - w / 2, w, w); g.globalAlpha = 1; } });
  }
  items.sort((a, b) => b.depth - a.depth);
  for (const it of items) it.draw();
  if (PLAYER.water) { g.fillStyle = 'rgba(30,70,190,.28)'; g.fillRect(0, 0, cv.width, cv.height); }
}

// ------------------------------------------------------------------ the loop
const WORLDS = [makeWorld(), makeWorld()];
let COMP = null;
function recompose() { COMP = composeProposal(); renderNotes(); }
function tick() {
  // the player
  if (PLAYER.swing > 0) PLAYER.swing--;
  if (PLAYER.hurt > 0) PLAYER.hurt--;
  if (PLAYER.walking) PLAYER.walkT++;
  if (PLAYER.airborne) {
    PLAYER.vy -= 0.08; PLAYER.y += PLAYER.vy;
    if (PLAYER.y <= 0) { PLAYER.y = 0; PLAYER.airborne = false; PLAYER.vy = 0;
      if (armourHas('feather')) for (let i = 0; i < 10; i++) { const a = i / 10 * TAU; spawn(WORLDS[1], 'poof', [Math.cos(a) * 0.3, 0.1, Math.sin(a) * 0.3], { v: [Math.cos(a) * 0.08, 0.01, Math.sin(a) * 0.08] }); } }
  }
  WORLDS.forEach((w, i) => {
    w.t++;
    if (w.t % SPEC.budget.runTicks === 0) { w.run++; if (i === 0) emitNow(w); else emitProposal(w, COMP); }
    if (i === 1) playerEvents(w);
    tickParticles(w);
  });
  for (let i = LATER.length - 1; i >= 0; i--) if (LATER[i].world.t >= LATER[i].at) { LATER[i].fn(); LATER.splice(i, 1); }
}
let acc = 0, last = performance.now();
function frame(now) {
  acc += Math.min(200, now - last); last = now;
  while (acc >= 50) { tick(); acc -= 50; }
  draw(CV[0], WORLDS[0]); draw(CV[1], WORLDS[1]);
  requestAnimationFrame(frame);
}
