import { base64ToUint8Array, uint8ArrayToBase64 } from "uint8array-extras";
import { WishDomainBase, WishDomainPartial } from "shared-schemas";
import { z } from "zod";
import {v5 as uuidv5} from 'uuid'
import { initLetters } from "../app/utils/letters";

export const WishSchema = WishDomainBase;

export const WishSchemaToSend = WishSchema.extend({
  picture: z.string()
});

export const WishSchemaToSendPartial = WishSchemaToSend.partial()
export const WishSchemaToSendArray = WishSchemaToSend.array()

export function convertBase64ToUint8(wish: z.infer<typeof WishSchemaToSend>): z.infer<typeof WishSchema> {
  return { ...wish, picture: base64ToUint8Array(wish.picture) };
}
export function convertBase64ToUint8Array(wishes: z.infer<typeof WishSchemaToSend>[]): z.infer<typeof WishSchema>[] {
  return wishes.map(convertBase64ToUint8);
}
type WishWithTagObjects = Omit<z.infer<typeof WishSchema>, 'tags'> & { tags: { id: string; tag: string }[] };

export function convertBase64ToUint8ArrayAndAddUUIDToTags(wish: z.infer<typeof WishSchemaToSend>): WishWithTagObjects {
  const w = convertBase64ToUint8(wish);
  return {
    ...w,
    tags: w.tags.map((tag, i) => ({ id: uuidv5(String(i), uuidv5.DNS), tag }))
  };
}
export function convertBase64ToUint8ArrayAndAddUUIDToTagsArray(wishes: z.infer<typeof WishSchemaToSend>[]): WishWithTagObjects[] {
  return wishes.map(convertBase64ToUint8ArrayAndAddUUIDToTags);
}
export function convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLetters(
  wish: z.infer<typeof WishSchemaToSend>
) {
  const w = convertBase64ToUint8ArrayAndAddUUIDToTags(wish);
  const name = { val: w.name, letters: initLetters(w.name) };
  const comment = { val: w.comment, letters: initLetters(w.comment) };
  const tags = w.tags.map((tag) => ({
    id: tag.id,
    val: tag.tag,
    letters: tag.tag.split('').map((c: string) => ({ letter: c, is_a_match: false }))
  }));
  return { ...w, name, comment, tags };
}
export function convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLettersArray(
  wishes: z.infer<typeof WishSchemaToSend>[]
) {
  return wishes.map(convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLetters);
}

export const WishSchemaOmitId = WishSchema.omit({id: true})

export function convertUint8ArrayToBase64(wish: z.infer<typeof WishSchemaOmitId>): Omit<z.infer<typeof WishSchemaToSend>, 'id'> {
  return { ...wish, picture: uint8ArrayToBase64(wish.picture) };
}

export const WishSchemaPartial = WishDomainPartial

export function convertUint8ArrayToBase64Partial(wish: z.input<typeof WishSchemaPartial>): z.infer<typeof WishSchemaToSend> | z.input<typeof WishSchemaPartial> {
  if (wish.picture instanceof Uint8Array) {
    return { ...wish, picture: uint8ArrayToBase64(wish.picture) } as any;
  }
  return wish;
}

export function convertPartialToFull(wish: z.input<typeof WishSchemaPartial>) {
  return WishSchema.parse(wish);
}

export type Wish = ReturnType<typeof convertBase64ToUint8ArrayAndAddUUIDToTagsAndAddLetters>;
export type WishWRate = Wish & {
  matchRate?: number;
  track_id?: string;
  highlightedName?: string | null;
  highlightedComment?: string | null;
}; 
export type WishOmitId = z.infer<typeof WishSchemaOmitId> ;
export type WishPartial = z.infer<typeof WishSchemaPartial> ;
export type WishToSend = z.infer<typeof WishSchemaToSend> ;
export type WishToSendPartial = z.infer<typeof WishSchemaToSendPartial> ;
export type t = ReturnType<typeof convertUint8ArrayToBase64Partial>;
export type Letter = {letter:string, is_a_match: boolean}