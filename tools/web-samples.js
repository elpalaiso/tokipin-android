// Prints the homepage's sample journal (../tokipin trial.js + trial-content.js) as JSON, in Korean,
// so Android's SampleDataTest can compare every date, kind and value with the web generator.
// Usage: node tools/web-samples.js <path to tokipin web repo>
const path = require('path');
const web = process.argv[2];
global.window = { addEventListener() {}, setTimeout: () => 0, clearTimeout() {} };
global.document = { documentElement: { lang: 'ko' }, addEventListener() {}, hidden: false };
require(path.resolve(web, 'trial-content.js'));
require(path.resolve(web, 'trial.js'));
const trial = window.TokipinTrial;
const days = trial.dates().map(date => ({ date, items: trial.get(date).map(({ kind, value }) => ({ kind, value })) }));
process.stdout.write(JSON.stringify({ today: trial.today, start: trial.start, days }));
