package main

import (
	"fmt"
	orderedMap "go-data-struct/map"
)

func main() {
	fmt.Println("Starting test...")
	ml := orderedMap.NewMapList()
	var a, b, c orderedMap.Keyer
	a = &orderedMap.Elements{Value: "Alice"}
	b = &orderedMap.Elements{Value: "Bob"}
	c = &orderedMap.Elements{Value: "Conrad"}
	ml.Push(a)
	ml.Push(b)
	ml.Push(c)
	cb := func(data orderedMap.Keyer) {
		fmt.Println(ml.DataMap[data.GetKey()].Value.(*orderedMap.Elements).Value)
	}
	fmt.Println("Print elements in the order of pushing:")
	ml.Walk(cb)
	fmt.Printf("Size of MapList: %d \n", ml.Size())
	ml.Remove(b)
	fmt.Println("After removing b:")
	ml.Walk(cb)
	fmt.Printf("Size of MapList: %d \n", ml.Size())
}
