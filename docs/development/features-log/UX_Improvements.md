# AI Food Logging — UX Improvements

Date: 2026-07-17

Follow-up UX work for the natural-language AI food logging feature (issue #419).
Verbatim transcript of the requested improvements.

## 1. Settings

We need to add a section into the app settings to properly configure the AI related
items. Also, we should provide with vendor prefilled options, so if I choose OpenAI,
the baseURL is prefilled, and I get a dropdown of models to choose, with the option to
manually override the model name (but initially should be a dropdown). Also the API Key
should be set up in this screen. If the api key has been already set up, then show
asterisks, with the ability for the user to set up a new key, in case they made a
mistake. Also a "TEST" button should be present, so the user can validate the settings.
Upon pressing the TEST button, a sample request should be made to the LLM (just something
so we know the network and settings are OK).

## 2. Food logging

After the response from the LLM, each food item should have the option to be stored as a
new Product. Also, each food should have a collapsible section, in which the user should
see the values of macronutrients (and fiber, etc) returned by the LLM, with the ability
to edit those. Finally, the user should be able to save the combination of foods as a new
Recipe. Saved foods should behave as the already existing products and recipes, with the
ability to edit and delete.