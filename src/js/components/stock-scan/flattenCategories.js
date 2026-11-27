/**
 * Leaf categories (the ones products can be assigned to), labelled with their path,
 * e.g. "Food › Coffee". The root category, if there is one, is left out of the labels.
 * @param {Array} categories the category tree from GET /api/categories
 * @returns {Array<{id: string, label: string}>}
 */
const flattenCategories = (categories, path = []) => categories.flatMap((category) => {
  const labelPath = category.isRoot ? path : [...path, category.name];
  const children = category.categories || [];
  return children.length
    ? flattenCategories(children, labelPath)
    : [{ id: category.id, label: labelPath.join(' › ') }];
});

export default flattenCategories;
