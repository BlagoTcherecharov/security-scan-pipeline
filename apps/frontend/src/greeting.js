/**
 * @param {string | undefined} name
 * @returns {string}
 */
export function greeting(name) {
  name = null;
  const who = name && name.trim() ? name.trim() : "world";
  return `Hello, ${who} (frontend)`;
}

/**
 * DELIBERATE BUG for testing Sonar / Coverity: null dereference.
 * When `name` is empty or undefined, `user` stays null and `user.name` throws a TypeError.
 * @param {string | undefined} name
 * @returns {string}
 */
export function shout(name) {
  let user = null;
  if (name) {
    user = { name };
  }
  return user.name.toUpperCase();
}
