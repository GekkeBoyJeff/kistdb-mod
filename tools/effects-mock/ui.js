'use strict';
// ------------------------------------------------------------------ kits to start from
const P = (material, ench, trim, dye) => ({ material, ench, trim: trim || null, dye: dye || null });
const T = (pattern, material) => ({ pattern, material });
const GOD = { head: { protection: 4, respiration: 3, aqua_affinity: 1, unbreaking: 3, mending: 1 }, chest: { protection: 4, thorns: 3, unbreaking: 3, mending: 1 },
  legs: { protection: 4, swift_sneak: 3, unbreaking: 3, mending: 1 }, feet: { protection: 4, feather_falling: 4, depth_strider: 3, soul_speed: 3, unbreaking: 3, mending: 1 } };
const SWORD = { kind: 'sword', ench: { sharpness: 5, fire_aspect: 2, looting: 3, sweeping_edge: 3, unbreaking: 3, mending: 1 } };
const PRESETS = [
  { name: 'God armour (diamant)', why: 'Het gewone eindspel: Protection overal, en per stuk iets extra. Nu zie je alleen Protection.',
    kit: { head: P('diamond', GOD.head), chest: P('diamond', GOD.chest), legs: P('diamond', GOD.legs), feet: P('diamond', GOD.feet), hand: SWORD } },
  { name: 'Fire Protection-set', why: 'Vier keer Fire Protection IV: een set. Nu een baan die ook langs de grond gaat.',
    kit: { head: P('diamond', { fire_protection: 4 }), chest: P('diamond', { fire_protection: 4 }), legs: P('diamond', { fire_protection: 4 }), feet: P('diamond', { fire_protection: 4 }), hand: { kind: 'sword', ench: { sharpness: 5 } } } },
  { name: 'Netherite, Silence-trims', why: 'Netherite + vuur = Infernal; Silence-trim + Swift Sneak = Warden\'s Shadow.',
    kit: { head: P('netherite', GOD.head, T('silence', 'amethyst')), chest: P('netherite', GOD.chest, T('silence', 'amethyst')), legs: P('netherite', GOD.legs, T('silence', 'amethyst')),
      feet: P('netherite', { ...GOD.feet, protection: 3 }, T('silence', 'amethyst')), hand: SWORD } },
  { name: 'Geverfd leer', why: 'Leer neemt de verfkleur mee; vier stukken: voetstappen in die kleur als je loopt.',
    kit: { head: P('leather', { protection: 2 }, null, '#B02E26'), chest: P('leather', { protection: 2 }, null, '#B02E26'), legs: P('leather', { protection: 2 }, null, '#B02E26'),
      feet: P('leather', { protection: 2, feather_falling: 2 }, null, '#B02E26'), hand: { kind: 'axe', ench: { efficiency: 3 } } } },
  { name: 'Gouden ridder (Smite)', why: 'Goud + Smite = Divine: een halo en goud licht.',
    kit: { head: P('gold', { protection: 3 }), chest: P('gold', { protection: 3 }), legs: P('gold', { protection: 3 }), feet: P('gold', { protection: 3 }), hand: { kind: 'sword', ench: { smite: 5, unbreaking: 3 } } } },
  { name: 'Koper + Channeling', why: 'Koper geleidt: twee koperen stukken + Channeling = Conductor.',
    kit: { head: P('copper', { protection: 2 }), chest: P('copper', { protection: 2 }), legs: P('iron', { protection: 3 }), feet: P('iron', { feather_falling: 3 }), hand: { kind: 'trident', ench: { channeling: 1, loyalty: 3, impaling: 4 } } } },
  { name: 'Vuur en ijs', why: 'Fire Aspect in je hand en Frost Walker aan je voeten: Steam.',
    kit: { head: P('iron', { protection: 3 }), chest: P('iron', { protection: 3 }), legs: P('iron', { protection: 3 }), feet: P('iron', { frost_walker: 2, protection: 3 }), hand: { kind: 'sword', ench: { fire_aspect: 2, sharpness: 4 } } } },
  { name: 'Storm (schildpad)', why: 'Turtle helmet + Respiration + Channeling: Storm boven je hoofd en Abyssal eromheen.',
    kit: { head: P('turtle_scute', { respiration: 3 }), chest: P('diamond', { protection: 4 }), legs: P('diamond', { protection: 4 }), feet: P('diamond', { depth_strider: 3 }), hand: { kind: 'trident', ench: { channeling: 1, loyalty: 3 } } } },
  { name: 'Elke trim een vorm', why: 'Spire (zuil), Rib (ribben), Shaper (vierkant), Host (puls), elk in zijn trimkleur.',
    kit: { head: P('diamond', { protection: 4, respiration: 3 }, T('spire', 'diamond')), chest: P('diamond', { thorns: 3 }, T('rib', 'redstone')), legs: P('diamond', { swift_sneak: 3 }, T('shaper', 'gold')),
      feet: P('diamond', { feather_falling: 4 }, T('host', 'emerald')), hand: null } },
  { name: 'Vier dezelfde trims', why: 'Vier keer Wild (emerald): Overgrowth, bladeren in de trimkleur. Met Silk Touch of Fortune in je hand wordt het Bloom.',
    kit: { head: P('iron', { protection: 4 }, T('wild', 'emerald')), chest: P('iron', { protection: 4 }, T('wild', 'emerald')), legs: P('iron', { protection: 4 }, T('wild', 'emerald')),
      feet: P('iron', { protection: 4, feather_falling: 4 }, T('wild', 'emerald')), hand: { kind: 'pickaxe', ench: { efficiency: 5, unbreaking: 3 } } } },
  { name: 'Paragon', why: 'Alles maxed (3+ enchantments per stuk), één materiaal, één trimpatroon: de zeldzaamste look.',
    kit: { head: P('diamond', { protection: 4, respiration: 3, aqua_affinity: 1, unbreaking: 3, mending: 1 }, T('ward', 'lapis')), chest: P('diamond', { protection: 4, thorns: 3, unbreaking: 3, mending: 1 }, T('ward', 'lapis')),
      legs: P('diamond', { protection: 4, swift_sneak: 3, unbreaking: 3, mending: 1 }, T('ward', 'lapis')), feet: P('diamond', { protection: 4, feather_falling: 4, depth_strider: 3, unbreaking: 3 }, T('ward', 'lapis')), hand: SWORD } },
  { name: 'Boog: Arcane', why: 'Power + Infinity: Arcane; schieten laat een spoor achter.',
    kit: { head: P('chainmail', { projectile_protection: 4 }), chest: P('chainmail', { projectile_protection: 4 }), legs: P('chainmail', { projectile_protection: 4 }), feet: P('chainmail', { projectile_protection: 4 }),
      hand: { kind: 'bow', ench: { power: 5, infinity: 1, punch: 2 } } } },
  { name: 'Overcharged (admin)', why: 'Boven het vanilla-maximum (Sharpness X, Fire Aspect V): een sterkere, gekleurde versie.',
    kit: { head: P('netherite', { protection: 8 }), chest: P('netherite', { protection: 8 }), legs: P('netherite', { protection: 8 }), feet: P('netherite', { protection: 8 }), hand: { kind: 'sword', ench: { sharpness: 10, fire_aspect: 5 } } } }
];

// ------------------------------------------------------------------ UI
const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
const CV = [$('#c0'), $('#c1')];
function fit() { for (const c of CV) { const r = c.getBoundingClientRect(); c.width = Math.round(r.width); c.height = Math.round(r.width * 0.78); } }
window.addEventListener('resize', fit);

function setKit(kit) {
  KIT = JSON.parse(JSON.stringify(kit));
  for (const k of ['head', 'chest', 'legs', 'feet', 'hand', 'off']) if (!(k in KIT)) KIT[k] = null;
  WORLDS.forEach(w => { w.parts = []; w.credits.clear(); });
  recompose(); renderEditor();
}
function renderPresets() {
  $('#presets').innerHTML = PRESETS.map((p, i) => `<button data-i="${i}">${p.name}</button>`).join('');
  $$('#presets button').forEach(b => b.onclick = () => { $$('#presets button').forEach(x => x.classList.remove('on')); b.classList.add('on'); $('#why').textContent = PRESETS[b.dataset.i].why; setKit(PRESETS[b.dataset.i].kit); });
}
const SLOT_MATS = { head: ['leather', 'chainmail', 'copper', 'iron', 'gold', 'diamond', 'netherite', 'turtle_scute'], chest: ['leather', 'chainmail', 'copper', 'iron', 'gold', 'diamond', 'netherite'],
  legs: ['leather', 'chainmail', 'copper', 'iron', 'gold', 'diamond', 'netherite'], feet: ['leather', 'chainmail', 'copper', 'iron', 'gold', 'diamond', 'netherite'] };
const fmtEnch = e => Object.entries(e || {}).map(([k, v]) => `${k} ${v}`).join(', ');
function parseEnch(s) { const o = {}; for (const part of s.split(',')) { const m = part.trim().match(/^([a-z_]+)\s*(\d+)?$/); if (m && MAXLV[m[1]]) o[m[1]] = +(m[2] || 1); } return o; }
function renderEditor() {
  const rows = ['head', 'chest', 'legs', 'feet'].map(s => {
    const p = KIT[s];
    const mat = `<select data-s="${s}" data-f="material"><option value="">—</option>${SLOT_MATS[s].map(m => `<option ${p && p.material === m ? 'selected' : ''} value="${m}">${SPEC.materials[m].name}</option>`).join('')}</select>`;
    const dye = p && p.material === 'leather' ? `<input type=color data-s="${s}" data-f="dye" value="${p.dye || '#A06540'}">` : '';
    const tp = `<select data-s="${s}" data-f="tp"><option value="">geen trim</option>${Object.keys(SPEC.trimPatterns).map(t => `<option ${p && p.trim && p.trim.pattern === t ? 'selected' : ''}>${t}</option>`).join('')}</select>`;
    const tm = `<select data-s="${s}" data-f="tm" ${p && p.trim ? '' : 'disabled'}>${Object.keys(SPEC.trimMaterials).map(t => `<option ${p && p.trim && p.trim.material === t ? 'selected' : ''}>${t}</option>`).join('')}</select>`;
    const en = `<input data-s="${s}" data-f="ench" list=enchs value="${fmtEnch(p && p.ench)}" placeholder="protection 4, thorns 3">`;
    return `<tr><th>${cap(s)}</th><td>${mat}${dye}</td><td>${tp}${tm}</td><td class=wide>${en}</td></tr>`;
  });
  const h = KIT.hand;
  rows.push(`<tr><th>Hand</th><td><select data-s="hand" data-f="kind"><option value="">—</option>${Object.keys(SPEC.items).map(k => `<option ${h && h.kind === k ? 'selected' : ''}>${k}</option>`).join('')}</select></td><td></td>
    <td class=wide><input data-s="hand" data-f="ench" list=enchs value="${fmtEnch(h && h.ench)}" placeholder="sharpness 5, fire_aspect 2"></td></tr>`);
  $('#editor').innerHTML = `<table>${rows.join('')}</table><datalist id=enchs>${Object.keys(MAXLV).map(e => `<option value="${e} ${MAXLV[e]}">`).join('')}</datalist>`;
  $$('#editor select, #editor input').forEach(el => el.onchange = () => {
    const s = el.dataset.s, f = el.dataset.f;
    if (s === 'hand') {
      if (f === 'kind') KIT.hand = el.value ? { kind: el.value, ench: KIT.hand ? KIT.hand.ench : {} } : null;
      else if (KIT.hand) KIT.hand.ench = parseEnch(el.value);
    } else {
      if (f === 'material') KIT[s] = el.value ? { ...(KIT[s] || { ench: {}, trim: null }), material: el.value } : null;
      else if (KIT[s]) {
        if (f === 'dye') KIT[s].dye = el.value;
        if (f === 'tp') KIT[s].trim = el.value ? { pattern: el.value, material: KIT[s].trim ? KIT[s].trim.material : 'gold' } : null;
        if (f === 'tm' && KIT[s].trim) KIT[s].trim.material = el.value;
        if (f === 'ench') KIT[s].ench = parseEnch(el.value);
      }
    }
    $$('#presets button').forEach(x => x.classList.remove('on')); $('#why').textContent = 'Eigen uitrusting.';
    WORLDS[1].credits.clear(); recompose(); renderEditor();
  });
}
function renderNotes() {
  $('#notes1').innerHTML = COMP.notes.length ? COMP.notes.map(([a, b]) => `<div class=row><span class=k>${a}</span><span>${b}</span></div>`).join('') : '<div class=dim>Niets: geen enchantments met een look.</div>';
  const now = composeNow();
  $('#notes0').innerHTML = now.length ? now.map(s => `<div class=row><span class=k>${{ HAND: 'Hand', SET: 'Set', ARMOUR: 'Harnas' }[s.kind]}</span><span><b>${SPEC.families[s.fam].name}</b> <span class=dim>${s.kind === 'ARMOUR' ? 'op ' + Math.round(s.h * 100) + '% van je lengte' : s.kind === 'SET' ? 'een baan van 8% tot 92% van je lengte' : 'voor je rechterhand'}</span></span></div>`).join('')
    + (now.length >= 3 ? '<div class=dim>Meer dan drie bronnen komen niet.</div>' : '') : '<div class=dim>Niets.</div>';
}
function bindControls() {
  const tog = (id, key) => { const b = $(id); b.onclick = () => { PLAYER[key] = !PLAYER[key]; b.classList.toggle('on', PLAYER[key]); }; };
  tog('#walk', 'walking'); tog('#sneak', 'sneaking'); tog('#water', 'water'); tog('#burn', 'burning');
  $('#swing').onclick = () => { PLAYER.swing = 6; event(WORLDS[1], 'swing'); };
  $('#kill').onclick = () => { PLAYER.swing = 6; event(WORLDS[1], 'swing'); later(WORLDS[1], 3, () => event(WORLDS[1], 'kill')); };
  $('#hurt').onclick = () => { PLAYER.hurt = 10; event(WORLDS[1], 'hurt'); };
  $('#fall').onclick = () => { if (!PLAYER.airborne) { PLAYER.airborne = true; PLAYER.vy = 0.9; } };
  $('#shoot').onclick = () => { PLAYER.swing = 4; event(WORLDS[1], 'shoot'); };
  $$('#cams button').forEach(b => b.onclick = () => { CAM.yaw = +b.dataset.yaw; CAM.pitch = +b.dataset.pitch; });
  $$('#intensity button').forEach(b => b.onclick = () => { INTENSITY = b.dataset.v; $$('#intensity button').forEach(x => x.classList.toggle('on', x === b)); recompose(); });
  let drag = null;
  for (const c of CV) {
    c.onpointerdown = e => { drag = { x: e.clientX, y: e.clientY, yaw: CAM.yaw, pitch: CAM.pitch }; c.setPointerCapture(e.pointerId); };
    c.onpointermove = e => { if (!drag) return; CAM.yaw = drag.yaw - (e.clientX - drag.x) * 0.01; CAM.pitch = Math.max(-0.1, Math.min(1.2, drag.pitch + (e.clientY - drag.y) * 0.008)); };
    c.onpointerup = () => drag = null;
    c.onwheel = e => { e.preventDefault(); CAM.dist = Math.max(2.5, Math.min(8, CAM.dist + e.deltaY * 0.004)); };
  }
}

// ------------------------------------------------------------------ the tables (from effects.json)
function sw(c) { return c && c[0] === '#' ? `<span class=sw style="background:${c}"></span>` : ''; }
function renderTables() {
  const F = SPEC.families;
  const ev = f => Object.entries(f.events || {}).map(([k, v]) => `<span class=tag>${k}</span>${v.p}`).join(' ');
  $('#t-fam').innerHTML = `<tr><th>Familie</th><th>Enchantments</th><th>Look</th><th>Bij max</th><th>Acties</th></tr>` + SPEC.order.map(id => { const f = F[id];
    return `<tr><td><b>${f.name}</b><div class=dim>${id}</div></td><td>${f.enchants.map(ENCH_NAME).join(', ')}</td>
      <td>${f.silent ? '<span class=dim>stil</span>' : `${sw(f.motif.color)}${f.motif.p} <span class=dim>${f.motif.motion}</span>${f.accentOnly ? ' <span class=tag>alleen als tweede</span>' : ''}`}</td>
      <td>${f.flourish ? sw(f.flourish.color) + f.flourish.p : ''}</td><td>${ev(f)}</td></tr>`; }).join('');
  $('#t-mat').innerHTML = `<tr><th>Materiaal</th><th>Kleur</th><th>Karakter</th><th>Set van vier</th></tr>` + Object.entries(SPEC.materials).map(([id, m]) =>
    `<tr><td><b>${m.name}</b></td><td>${m.color === 'dye' ? 'de verfkleur' : sw(m.color) + m.color}${m.accent ? ' ' + sw(m.accent) : ''}</td><td>snelheid ${m.character.speed} · grootte ${m.character.size}${m.character.sparkle ? ' · glinster ' + m.character.sparkle : ''}</td>
     <td>${m.set ? `<b>${m.set.name}</b> <span class=dim>${m.set.shape} · ${m.set.p}${m.set.when ? ' · als je ' + (m.set.when === 'walking' ? 'loopt' : 'beweegt') : ''}</span>` : m.gliding ? 'tijdens het vliegen: sporen uit de vleugelpunten' : ''}</td></tr>`).join('');
  $('#t-tm').innerHTML = `<tr><th>Trim-materiaal</th><th>Kleur</th><th>Glinster</th></tr>` + Object.entries(SPEC.trimMaterials).map(([id, m]) =>
    `<tr><td><b>${id}</b></td><td>${sw(m.color)}${m.color}</td><td>${sw(m.sparkle.color)}${m.sparkle.p}</td></tr>`).join('');
  $('#t-tp').innerHTML = `<tr><th>Trim-patroon</th><th>Uit</th><th>Vorm per stuk</th><th>Set van vier</th></tr>` + Object.entries(SPEC.trimPatterns).map(([id, t]) =>
    `<tr><td><b>${id}</b></td><td class=dim>${t.from}</td><td>${t.shape}${t.motion ? ' · ' + t.motion : ''}${t.hush ? ' · dempt de rest' : ''}</td><td><b>${t.set.name}</b> <span class=dim>${t.set.shape} · ${t.set.p}</span></td></tr>`).join('');
  $('#t-combo').innerHTML = `<tr><th>Combinatie</th><th>Soort</th><th>Wanneer</th><th>Look</th></tr>` + SPEC.combos.map(c =>
    `<tr><td><b>${c.name}</b></td><td><span class=tag>${{ legend: 'legende', material: 'materiaal', trim: 'trim', enchant: 'enchantment' }[c.class]}</span></td><td>${comboWhy(c)}</td>
     <td>${c.look.shape} · ${c.look.p}${c.look.alternate ? ' / ' + c.look.alternate : ''}${(c.look.extra || []).map(e => ' + ' + e.p).join('')}</td></tr>`).join('');
}

fit(); renderPresets(); renderTables(); bindControls();
$$('#presets button')[0].click();
requestAnimationFrame(frame);
